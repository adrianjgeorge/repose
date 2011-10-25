package org.openrepose.rnxp.decoder;

/**
 *
 * @author zinic
 */
public enum DecoderState {

    // Request line states
    READ_SC_PARSE_METHOD,
    READ_MC_PARSE_METHOD,
    READ_URI,
    READ_VERSION,
    READ_REQUEST_LINE_CR,
    READ_REQUEST_LINE_LF,
    
    // Header line states
    READ_HEADER_KEY,
    READ_HEADER_VALUE,
    READ_HEADER_CR,
    READ_HEADER_LF,
    READ_HEADER_FINAL_LF,
    
    // Body states
    STREAM_REMAINING,
    READ_END
}
