package com.aresstack.ollamamanager.infrastructure.runtime;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OutputPumpTest {

    @Test
    public void splitsLinesAndBoundsLinesWithoutNewline() throws Exception {
        assertEquals(Arrays.asList("a", "b", "c", "d"), pump(stream("a\nb\r\nc\rd")));

        InputStream endless = new InputStream() {
            private long remaining = 50L * 1024 * 1024;

            @Override
            public int read() {
                return remaining-- > 0 ? 'x' : -1;
            }
        };
        List<String> lines = pump(endless);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).length() <= BoundedLog.MAX_LINE_LENGTH + 6);
        assertTrue(lines.get(0).endsWith(" [...]"));
    }

    private static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> pump(InputStream input) throws InterruptedException {
        BoundedLog log = new BoundedLog(10);
        Thread thread = OutputPump.start(input, log, "test");
        thread.join(60000);
        return log.snapshot();
    }
}
