package org.openrepose.commons.utils.classloader

import java.io.{FileOutputStream, IOException, File}
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{SimpleFileVisitor, Path, FileVisitResult, Files}

import com.typesafe.config.ConfigFactory
import org.apache.logging.log4j.{Level, LogManager}
import org.apache.logging.log4j.core.LoggerContext
import org.apache.logging.log4j.test.appender.ListAppender
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{FunSpec, Matchers}

import scala.util.Random

@RunWith(classOf[JUnitRunner])
class EarClassProviderTest extends FunSpec with Matchers {

  val logContext = LogManager.getContext(false).asInstanceOf[LoggerContext]
  val appender = logContext.getConfiguration.getAppender("List0").asInstanceOf[ListAppender]

  /**
   * A scalaish adaptation of http://stackoverflow.com/questions/779519/delete-files-recursively-in-java/8685959#8685959
   * This is Java7 Dependent
   * As specified in the notes, it's a fail fast, rather than a try hardest.
   * That's okay, we shouldn't be using this outside of test directories
   * @param path
   * @return
   */
  def deleteRecursive(path: Path) = {
    Files.walkFileTree(path, new SimpleFileVisitor[Path]() {
      override def visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def visitFileFailed(file: Path, exc: IOException): FileVisitResult = {
        Files.delete(file)
        FileVisitResult.CONTINUE
      }

      override def postVisitDirectory(dir: Path, exc: IOException): FileVisitResult = {
        Option(exc) match {
          case None =>
            Files.delete(dir)
            FileVisitResult.CONTINUE
          case Some(x) =>
            throw x
        }
      }
    })
  }

  val testProps = ConfigFactory.load("test.properties")
  val version = testProps.getString("coreTestFilterBundleVersion")
  val earFile = new File(testProps.getString("coreTestFilterBundleLocation"), s"core-test-filter-bundle-${version}.ear")

  def withTempDir(f: (File) => Unit) = {
    def tempDir(): File = {
      val f = Files.createTempDirectory("earUnpackRoot").toFile
      f.deleteOnExit()
      f
    }
    val t = tempDir()
    try {
      f(t)
    } finally {
      deleteRecursive(t.toPath)
    }
  }


  it("unpacks an ear to a directory successfully") {
    import scala.collection.JavaConversions._

    withTempDir { root =>
      val p = new EarClassProvider(earFile, root)

      p.unpack()

      root.listFiles.toList shouldNot be(empty)

      val files = p.outputDir.listFiles.toList

      files should contain(new File(p.outputDir, s"core-test-filter-${version}.jar"))
      files should contain(new File(p.outputDir, "WEB-INF"))

      val webInfDir = new File(p.outputDir, "WEB-INF")
      webInfDir.listFiles.toList should contain(new File(webInfDir, "web-fragment.xml"))

    }
  }

  it("logs a warning if extraction failed") {
    import scala.collection.JavaConversions._

    appender.clear()

    val tempFile = File.createTempFile("junk", ".ear")
    tempFile.deleteOnExit()
    withTempDir { root =>
      val range = 0 to 2048
      val fos = new FileOutputStream(tempFile)
      range.foreach { i =>
        fos.write(Random.nextInt())
      }
      fos.close()

      val p = new EarClassProvider(tempFile, root)
      intercept[IOException] {
        p.unpack()
      }

      //Verify that a warning was logged
      appender.getEvents.size() should be(1)

      val event = appender.getEvents.toList.head
      event.getLevel should be(Level.WARN)


    }

  }

  it("throws an IO Exception if unable to unpack the ear to the specified directory") {
    //create garbage file
    val tempFile = File.createTempFile("junk", ".ear")
    tempFile.deleteOnExit()

    withTempDir { root =>
      val range = 0 to 2048
      val fos = new FileOutputStream(tempFile)
      range.foreach { i =>
        fos.write(Random.nextInt())
      }
      fos.close()

      val p = new EarClassProvider(tempFile, root)
      intercept[IOException] {
        p.unpack()
      }
    }
  }

  it("throws an IO Exception if the Ear file doesn't exist") {
    withTempDir { root =>
      val notAFile = new File("this file doesn't exist.txt")

      val p = new EarClassProvider(notAFile, root)
      intercept[IOException] {
        p.unpack()
      }
    }
  }

  it("if the unpack root doesn't exist we get an IOException") {
    val root = new File("/derp/derp/derp")

    val p = new EarClassProvider(earFile, root)

    intercept[IOException] {
      p.unpack()
    }
  }

  it("provides a class that is not in the current classloader") {
    pending
  }

  it("cleans up it's unpacked artifacts") {
    pending
  }

  it("throws a ClassNotFoundException when you ask for a class that isn't in the ear") {
    pending
  }

  it("multiple ear files don't share classes") {
    pending
  }

  it("can get the web-fragment.xml") {
    pending
  }

  describe("in the context of spring") {
    it("when given to a AppContext beans are provided") {
      pending
    }
  }
}
