package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RawHttpRequestTest {

    @Test
    public void readsChunkedBody() throws Exception {
        RawHttpRequest.Response response = parse("HTTP/1.1 404 Not Found\r\nTransfer-Encoding: chunked\r\n\r\n"
            + "5\r\n{\"err\r\n7;ext=1\r\nor\":1}\n\r\n0\r\n\r\n");
        assertEquals(404, response.status());
        assertEquals("{\"error\":1}\n", body(response));
    }

    @Test
    public void readsFixedLengthAndCloseDelimitedBodies() throws Exception {
        assertEquals("ab", body(parse("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nabcd")));
        assertEquals("abcd", body(parse("HTTP/1.0 200 OK\r\n\r\nabcd")));
    }

    @Test
    public void rejectsNonHttpAnswers() throws Exception {
        try {
            parse("SSH-2.0-OpenSSH\r\n\r\n");
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.NOT_OLLAMA, e.kind());
        }
    }

    @Test(expected = IOException.class)
    public void rejectsTruncatedChunk() throws Exception {
        body(parse("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n10\r\nabc"));
    }

    private static RawHttpRequest.Response parse(String raw) throws Exception {
        return RawHttpRequest.readHead(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)), () -> { });
    }

    private static String body(RawHttpRequest.Response response) throws Exception {
        return HttpOllamaApiClient.readBody(response.body());
    }
}
