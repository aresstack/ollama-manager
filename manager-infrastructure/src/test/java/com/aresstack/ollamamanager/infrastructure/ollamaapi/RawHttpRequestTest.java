package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RawHttpRequestTest {

    @Test
    public void readsChunkedBody() throws Exception {
        RawHttpRequest.Response response = parse("HTTP/1.1 404 Not Found\r\nTransfer-Encoding: chunked\r\n\r\n"
            + "5\r\n{\"err\r\n7;ext=1\r\nor\":1}\n\r\n0\r\n\r\n");
        assertEquals(404, response.status());
        assertEquals("{\"error\":1}\n", response.body());
    }

    @Test
    public void readsFixedLengthAndCloseDelimitedBodies() throws Exception {
        assertEquals("ab", parse("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nabcd").body());
        assertEquals("abcd", parse("HTTP/1.0 200 OK\r\n\r\nabcd").body());
    }

    @Test
    public void rejectsNonHttpAndOversizedAnswers() throws Exception {
        try {
            parse("SSH-2.0-OpenSSH\r\n\r\n");
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.NOT_OLLAMA, e.kind());
        }
        try {
            RawHttpRequest.readResponse(stream("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\n0123456789"), 4);
            fail();
        } catch (OperationException e) {
            assertEquals(ErrorKind.NOT_OLLAMA, e.kind());
        }
    }

    private static RawHttpRequest.Response parse(String raw) throws Exception {
        return RawHttpRequest.readResponse(stream(raw), 1024);
    }

    private static ByteArrayInputStream stream(String raw) {
        return new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8));
    }
}
