package com.aresstack.ollamamanager.infrastructure.ollamaapi;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

/**
 * Creates unconnected sockets with {@link Proxy#NO_PROXY}. OkHttp uses the socket factory only after its
 * proxy selector chose a direct or HTTP route; a plain {@code new Socket()} would consult the JVM default
 * {@link java.net.ProxySelector} again for SOCKS and could undo that choice.
 */
final class DirectSocketFactory extends SocketFactory {

    @Override
    public Socket createSocket() {
        return new Socket(Proxy.NO_PROXY);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return connected(new InetSocketAddress(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localAddress, int localPort) throws IOException {
        Socket socket = createSocket();
        socket.bind(new InetSocketAddress(localAddress, localPort));
        socket.connect(new InetSocketAddress(host, port));
        return socket;
    }

    @Override
    public Socket createSocket(InetAddress address, int port) throws IOException {
        return connected(new InetSocketAddress(address, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort)
        throws IOException {
        Socket socket = createSocket();
        socket.bind(new InetSocketAddress(localAddress, localPort));
        socket.connect(new InetSocketAddress(address, port));
        return socket;
    }

    private Socket connected(InetSocketAddress address) throws IOException {
        Socket socket = createSocket();
        socket.connect(address);
        return socket;
    }
}
