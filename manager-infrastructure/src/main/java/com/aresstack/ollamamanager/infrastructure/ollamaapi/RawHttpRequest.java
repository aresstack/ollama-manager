package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.server.Endpoint;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Minimal HTTP/1.1 transport on a plain socket. It replaces {@link java.net.HttpURLConnection} because on
 * Java 8 that class refuses a body on {@code DELETE} (JDK-7157360), and its {@code disconnect()} can wait for
 * a reader's lock instead of aborting a blocked read. Here cancellation closes the socket, which unblocks
 * any read immediately. Sends {@code Connection: close}; supports fixed-length, chunked and close-delimited
 * responses.
 */
final class RawHttpRequest {

    private static final int MAX_HEADER_LINE = 8192;
    private static final int MAX_HEADERS = 100;

    private RawHttpRequest() {
    }

    /**
     * Send a request and read the status and headers. The caller must close the returned response.
     *
     * @param body request body, or {@code null} for none
     */
    static Response open(Endpoint endpoint, String method, String path, byte[] body, int connectTimeoutMillis,
                         int readTimeoutMillis, CancellationToken cancellation)
        throws IOException, OperationException {
        final Socket socket = new Socket();
        cancellation.onCancel(() -> closeQuietly(socket));
        boolean opened = false;
        try {
            try {
                socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), connectTimeoutMillis);
            } catch (IOException e) {
                if (cancellation.isCancelled()) {
                    throw e;
                }
                throw new OperationException(ErrorKind.CONNECTION_FAILED, "cannot connect to Ollama at "
                    + endpoint, e);
            }
            socket.setSoTimeout(readTimeoutMillis);
            OutputStream out = socket.getOutputStream();
            StringBuilder head = new StringBuilder()
                .append(method).append(' ').append(path).append(" HTTP/1.1\r\n")
                .append("Host: ").append(endpoint.toHostValue()).append("\r\n")
                .append("Accept: application/json, application/x-ndjson\r\n")
                .append("Connection: close\r\n");
            if (body != null) {
                head.append("Content-Type: application/json\r\n")
                    .append("Content-Length: ").append(body.length).append("\r\n");
            }
            head.append("\r\n");
            out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
            if (body != null) {
                out.write(body);
            }
            out.flush();
            Response response = readHead(new BufferedInputStream(socket.getInputStream()), socket);
            opened = true;
            return response;
        } finally {
            if (!opened) {
                closeQuietly(socket);
            }
        }
    }

    /** Parse status line and headers from {@code in}; {@code resource} is closed with the response. */
    static Response readHead(InputStream in, Closeable resource) throws IOException, OperationException {
        String statusLine = readLine(in);
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/1.")) {
            throw new OperationException(ErrorKind.NOT_OLLAMA, "not an HTTP response: " + statusLine);
        }
        int status;
        try {
            status = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new OperationException(ErrorKind.NOT_OLLAMA, "invalid HTTP status: " + statusLine, e);
        }
        long contentLength = -1;
        boolean chunked = false;
        String line;
        int headers = 0;
        while (!(line = readLine(in)).isEmpty()) {
            if (++headers > MAX_HEADERS) {
                throw new OperationException(ErrorKind.NOT_OLLAMA, "too many HTTP headers");
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                throw new OperationException(ErrorKind.NOT_OLLAMA, "invalid HTTP header: " + line);
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            if (name.equals("content-length")) {
                try {
                    contentLength = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new OperationException(ErrorKind.NOT_OLLAMA, "invalid Content-Length: " + value, e);
                }
            } else if (name.equals("transfer-encoding")) {
                chunked = value.toLowerCase(Locale.ROOT).contains("chunked");
            }
        }
        InputStream body;
        if (chunked) {
            body = new ChunkedInputStream(in);
        } else if (contentLength >= 0) {
            body = new LengthLimitedInputStream(in, contentLength);
        } else {
            body = in;
        }
        return new Response(status, body, resource);
    }

    static String readLine(InputStream in) throws IOException, OperationException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                int end = line.length();
                return end > 0 && line.charAt(end - 1) == '\r' ? line.substring(0, end - 1) : line.toString();
            }
            if (line.length() >= MAX_HEADER_LINE) {
                throw new OperationException(ErrorKind.NOT_OLLAMA, "HTTP header line too long");
            }
            line.append((char) c);
        }
        throw new IOException("connection closed inside HTTP header");
    }

    private static void closeQuietly(Closeable resource) {
        try {
            resource.close();
        } catch (IOException ignored) {
            // Closing is best effort.
        }
    }

    /** Status and decoded body stream of a response. Closing it closes the connection. */
    static final class Response implements Closeable {
        private final int status;
        private final InputStream body;
        private final Closeable resource;

        Response(int status, InputStream body, Closeable resource) {
            this.status = status;
            this.body = body;
            this.resource = resource;
        }

        int status() {
            return status;
        }

        /** Return the body with transfer encoding removed; it ends at the end of the message. */
        InputStream body() {
            return body;
        }

        @Override
        public void close() {
            closeQuietly(resource);
        }
    }

    /** Decodes {@code Transfer-Encoding: chunked}; ends at the zero-size chunk. */
    static final class ChunkedInputStream extends InputStream {
        private final InputStream in;
        private long remaining;
        private boolean finished;

        ChunkedInputStream(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (finished) {
                return -1;
            }
            if (remaining == 0) {
                remaining = nextChunkSize();
                if (remaining == 0) {
                    finished = true;
                    return -1;
                }
            }
            int n = in.read(buffer, offset, (int) Math.min(length, remaining));
            if (n == -1) {
                throw new IOException("connection closed inside chunk");
            }
            remaining -= n;
            if (remaining == 0) {
                expectLineEnd();
            }
            return n;
        }

        private long nextChunkSize() throws IOException {
            String line = line();
            int extension = line.indexOf(';');
            try {
                long size = Long.parseLong((extension < 0 ? line : line.substring(0, extension)).trim(), 16);
                if (size < 0) {
                    throw new NumberFormatException();
                }
                return size;
            } catch (NumberFormatException e) {
                throw new IOException("invalid chunk size: " + line, e);
            }
        }

        private void expectLineEnd() throws IOException {
            if (!line().isEmpty()) {
                throw new IOException("missing CRLF after chunk");
            }
        }

        private String line() throws IOException {
            try {
                return readLine(in);
            } catch (OperationException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    }

    /** Ends after a fixed number of bytes. */
    static final class LengthLimitedInputStream extends FilterInputStream {
        private long remaining;

        LengthLimitedInputStream(InputStream in, long length) {
            super(in);
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n == -1 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int n = in.read(buffer, offset, (int) Math.min(length, remaining));
            if (n == -1) {
                throw new IOException("connection closed before end of body");
            }
            remaining -= n;
            return n;
        }
    }
}
