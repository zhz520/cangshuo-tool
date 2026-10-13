package com.cangshuo.toolbox.media;

import com.cangshuo.toolbox.common.exception.*;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MediaHttpBoundaryTest {
    @Test void binaryCdnContentRequiresKnownSignaturesNotHtmlOrJson() {
        assertEquals("image/jpeg",MediaHttp.sniffType(new byte[]{(byte)255,(byte)216,(byte)255}));
        assertEquals("image/png",MediaHttp.sniffType(new byte[]{(byte)137,80,78,71,13,10,26,10}));
        assertEquals("image/gif",MediaHttp.sniffType("GIF89a".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("image/webp",MediaHttp.sniffType("RIFF0000WEBP".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("video/mp4",MediaHttp.sniffType(new byte[]{0,0,0,24,102,116,121,112,105,115,111,109}));
        assertEquals("video/quicktime",MediaHttp.sniffType(new byte[]{0,0,0,24,102,116,121,112,113,116,32,32}));
        for (String invalid : new String[]{"<!DOCTYPE html>","{\"error\":true}","%PDF-1.7","RIFF0000WAVE","", "ftyp"}) assertEquals("",MediaHttp.sniffType(invalid.getBytes(StandardCharsets.US_ASCII)));
    }
    @Test void upstreamRangesMustMatchRequestedStartLengthAndTotalBudget() {
        assertDoesNotThrow(() -> MediaHttp.validateContentRange(206,"bytes=0-","bytes 0-9/10",10));
        assertDoesNotThrow(() -> MediaHttp.validateContentRange(206,"bytes=5-9","bytes 5-9/10",5));
        assertDoesNotThrow(() -> MediaHttp.validateContentRange(200,"bytes=0-",null,100));
        for (String range : new String[]{"bytes 1-9/10", "bytes 0-10/10", "bytes 0-9/9", "bytes 0-9/*", "bytes -1-9/10", "bytes 0-9/999999999999999", "bytes 0-1/10\r\nX:test"})
            assertEquals(ApiError.MEDIA_PROVIDER_ERROR,assertThrows(ApiException.class,() -> MediaHttp.validateContentRange(206,"bytes=0-",range,10)).error());
        assertThrows(ApiException.class,() -> MediaHttp.validateContentRange(206,"bytes=0-1","bytes 0-2/10",3));
        assertThrows(ApiException.class,() -> MediaHttp.validateContentRange(206,null,"bytes 0-1/10",2));
        assertThrows(ApiException.class,() -> MediaHttp.validateContentRange(206,"bytes=0-1","bytes 0-1/10",1));
        assertEquals(ApiError.MEDIA_TOO_LARGE,assertThrows(ApiException.class,() -> MediaHttp.validateContentRange(206,"bytes=0-1","bytes 0-1/209715201",2)).error());
    }
}
