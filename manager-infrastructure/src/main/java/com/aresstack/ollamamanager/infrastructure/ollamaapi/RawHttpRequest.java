package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import com.aresstack.ollamamanager.application.common.CancellationToken;
import com.aresstack.ollamamanager.application.common.ErrorKind;
import com.aresstack.ollamamanager.application.common.OperationException;
import com.aresstack.ollamamanager.domain.server.Endpoint;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Minimal HTTP/1.1 client for the one request {@link java.net.HttpURLConnection} cannot send on Java 8:
 * a {@code DELETE} with a body. Sends {@code Connection: close} and supports fixed-length, chunked and
 * close-delimited responses.
 */
final class RawHttpRequest {

    private static final int MAX_HEADER_LINE = 8192;

    private RawHttpRequest() {
    }

    static Response send(Endpoint endpoint, String method, String path, byte[] body, int connectTimeoutMillis,
                         int readTimeoutMillis, int maxBodyBytes, CancellationToken cancellation)
        throws IOException, OperationException {
        final Socket socket = new Socket();
        cancellation.onCancel(() -> closeQuietly(socket));
        try {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()), connectTimeoutMillis);
            socket.setSoTimeout(readTimeoutMillis);
            OutputStream out = socket.getOutputStream();
            String head = method + " " + path + " HTTP/1.1\r\n"
                + "Host: " + endpoint.toHostValue() + "\r\n"
                + "Content-Type: application/json\r\n"
                + "Accept: application/json\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
            return readResponse(new BufferedInputStream(socket.getInputStream()), maxBodyBytes);
        } finally {
            closeQuietly(socket);
        }
    }

    static Response readResponse(InputStream in, int maxBodyBytes) throws IOException, OperationException {
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
        while (!(line = readLine(in)).isEmpty()) {
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
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        if (chunked) {
            long size;
            while ((size = chunkSize(readLine(in))) > 0) {
                copy(in, body, size, maxBodyBytes);
                readLine(in);
            }
        } else if (contentLength >= 0) {
            copy(in, body, contentLength, maxBodyBytes);
        } else {
            copy(in, body, Long.MAX_VALUE, maxBodyBytes);
        }
        return new Response(status, new String(body.toByteArray(), StandardCharsets.UTF_8));
    }

    private static long chunkSize(String line) throws OperationException {
        int extension = line.indexOf(';');
        try {
            return Long.parseLong((extension < 0 ? line : line.substring(0, extension)).trim(), 16);
        } catch (NumberFormatException e) {
            throw new OperationException(ErrorKind.NOT_OLLAMA, "invalid chunk size: " + line, e);
        }
    }

    private static void copy(InputStream in, ByteArrayOutputStream out, long length, int maxBodyBytes)
        throws IOException, OperationException {
        byte[] buffer = new byte[8192];
        long remaining = length;
        while (remaining > 0) {
            int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read == -1) {
                if (length == Long.MAX_VALUE) {
                    return;
                }
                throw new IOException("connection closed before end of body");
            }
            if (out.size() + read > maxBodyBytes) {
                throw new OperationException(ErrorKind.NOT_OLLAMA, "response exceeds " + maxBodyBytes + " bytes");
            }
            out.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static String readLine(InputStream in) throws IOException, OperationException {
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

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing is best effort.
        }
    }

    static final class Response {
        private final int status;
        private final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }

        int status() {
            return status;
        }

        String body() {
            return body;
        }
    }
}
