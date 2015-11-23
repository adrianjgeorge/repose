package features.filters.urlextractortoheader

import framework.ReposeValveTest
import framework.mocks.MockIdentityService
import framework.mocks.MockValkyrie
import org.rackspace.deproxy.Deproxy
import org.rackspace.deproxy.MessageChain
import static org.junit.Assert.*;
import spock.lang.Unroll

/**
 * Created by jennyvo on 11/23/15.
 *  Test valkyrie without need of validator but using url-extractor-to-header
 */
class UrlExtractorToHeaderWValkyrieTest extends ReposeValveTest {
    def static originEndpoint
    def static identityEndpoint
    def static valkyrieEndpoint

    def static MockIdentityService fakeIdentityService
    def static MockValkyrie fakeValkyrie
    def static Map params = [:]

    def static random = new Random()

    def setupSpec() {
        deproxy = new Deproxy()

        params = properties.getDefaultTemplateParams()
        repose.configurationProvider.cleanConfigDirectory()
        repose.configurationProvider.applyConfigs("common", params);
        repose.configurationProvider.applyConfigs("features/filters/urlextractortoheader", params);
        repose.configurationProvider.applyConfigs("features/filters/urlextractortoheader/wvalkyrie", params);

        repose.start()

        originEndpoint = deproxy.addEndpoint(properties.targetPort, 'origin service')
        fakeIdentityService = new MockIdentityService(properties.identityPort, properties.targetPort)
        identityEndpoint = deproxy.addEndpoint(properties.identityPort, 'identity service', null, fakeIdentityService.handler)
        fakeIdentityService.checkTokenValid = true

        fakeValkyrie = new MockValkyrie(properties.valkyriePort)
        valkyrieEndpoint = deproxy.addEndpoint(properties.valkyriePort, 'valkyrie service', null, fakeValkyrie.handler)
    }

    def setup() {
        fakeIdentityService.resetHandlers()
        fakeIdentityService.resetDefaultParameters()
        fakeValkyrie.resetHandlers()
        fakeValkyrie.resetParameters()
    }

    def cleanupSpec() {
        if (deproxy) {
            deproxy.shutdown()
        }

        if (repose) {
            repose.stop()
        }
    }


    @Unroll("permission: #permission for #method with tenant: #tenantID and deviceID: #deviceID should return a #responseCode")
    def "Test Valkyrie with url-extractor-to-header"() {
        given: "A device ID with a particular permission level defined in Valkyrie"

        fakeIdentityService.with {
            client_apikey = UUID.randomUUID().toString()
            client_token = UUID.randomUUID().toString()
            client_tenant = tenantID
        }

        fakeValkyrie.with {
            device_id = deviceID
            device_perm = permission
        }

        when: "a request is made against a device with Valkyrie set permissions"
        MessageChain mc = deproxy.makeRequest(url: reposeEndpoint + "/resource/" + deviceID, method: method,
                headers: [
                        'content-type': 'application/json',
                        'X-Auth-Token': fakeIdentityService.client_token,
                ]
        )

        then: "check response"
        mc.receivedResponse.code == responseCode
        if (responseCode.equals("200")) {
            assertTrue(mc.handlings.get(0).request.headers.contains("x-device-id"))
            assertTrue(mc.handlings.get(0).request.headers.getFirstValue("x-device-id") == deviceID)
        }
        //**This for tracing header on failed response REP-2147
        mc.receivedResponse.headers.contains("x-trans-id")
        //**This part for tracing header test REP-1704**
        // any requests send to identity also include tracing header
        mc.orphanedHandlings.each {
            e -> assertTrue(e.request.headers.contains("x-trans-id"))
        }

        where:
        method   | tenantID                   | deviceID | permission      | responseCode
        "GET"    | randomTenant()             | "520707" | "view_product"  | "200"
        "HEAD"   | randomTenant()             | "520707" | "view_product"  | "200"
        "GET"    | randomTenant() - "hybrid:" | "520707" | "view_product"  | "403"
        "PUT"    | randomTenant()             | "520707" | "view_product"  | "403"
        "POST"   | randomTenant()             | "520707" | "view_product"  | "403"
        "DELETE" | randomTenant()             | "520707" | "view_product"  | "403"
        "PATCH"  | randomTenant()             | "520707" | "view_product"  | "403"
        "GET"    | randomTenant()             | "520707" | "admin_product" | "200"
        "HEAD"   | randomTenant()             | "520707" | "admin_product" | "200"
        "PUT"    | randomTenant()             | "520707" | "admin_product" | "200"
        "POST"   | randomTenant()             | "520707" | "admin_product" | "200"
        "PATCH"  | randomTenant()             | "520707" | "admin_product" | "200"
        "DELETE" | randomTenant()             | "520707" | "admin_product" | "200"
        "GET"    | randomTenant()             | "520707" | "edit_product"  | "200"
        "HEAD"   | randomTenant()             | "520707" | "edit_product"  | "200"
        "PUT"    | randomTenant()             | "520707" | "edit_product"  | "200"
        "POST"   | randomTenant()             | "520707" | "edit_product"  | "200"
        "PATCH"  | randomTenant()             | "520707" | "edit_product"  | "200"
        "DELETE" | randomTenant()             | "520707" | "edit_product"  | "200"
        "GET"    | randomTenant()             | "520707" | ""              | "403"
        "HEAD"   | randomTenant()             | "520707" | ""              | "403"
        "PUT"    | randomTenant()             | "520707" | ""              | "403"
        "POST"   | randomTenant()             | "520707" | ""              | "403"
        "PATCH"  | randomTenant()             | "520707" | ""              | "403"
        "DELETE" | randomTenant()             | "520707" | ""              | "403"
        "GET"    | randomTenant()             | "520707" | "shazbot_prod"  | "403"
        "HEAD"   | randomTenant()             | "520707" | "prombol"       | "403"
        "PUT"    | randomTenant()             | "520707" | "hezmol"        | "403"
        "POST"   | randomTenant()             | "520707" | "_22_reimer"    | "403"
        "PATCH"  | randomTenant()             | "520707" | "blah"          | "403"
        "DELETE" | randomTenant()             | "520707" | "blah"          | "403"

    }

    def String randomTenant() {
        "hybrid:" + random.nextInt()
    }
}
