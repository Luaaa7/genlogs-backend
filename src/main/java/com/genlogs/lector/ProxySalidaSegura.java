package com.genlogs.lector;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;

/** HTTPS CONNECT: valida cada destino y conecta a la IP validada, sin resolverla otra vez. */
final class ProxySalidaSegura implements AutoCloseable {
    @FunctionalInterface interface Resolver { InetAddress[] resolver(String host) throws IOException; }
    private final Resolver resolver;
    private final ServerSocket server;
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = new ThreadPoolExecutor(0, 64, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), r -> { Thread t = new Thread(r, "lector-egress"); t.setDaemon(true); return t; });
    private volatile boolean closed;

    ProxySalidaSegura() throws IOException { this(InetAddress::getAllByName); }
    ProxySalidaSegura(Resolver resolver) throws IOException {
        this.resolver = resolver;
        server = new ServerSocket();
        server.bind(new InetSocketAddress("127.0.0.1", 0));
        Thread accept = new Thread(this::accept, "lector-proxy"); accept.setDaemon(true); accept.start();
    }
    String url() { return "http://127.0.0.1:" + server.getLocalPort(); }
    private void accept() {
        while (!closed) {
            Socket client = null;
            try {
                client = server.accept(); client.setSoTimeout(45000); sockets.add(client);
                Socket accepted = client;
                workers.execute(() -> tunnel(accepted));
            } catch (IOException | RejectedExecutionException e) { closeSocket(client); }
        }
    }
    private void tunnel(Socket client) {
        Socket upstream = null;
        try {
            String header = readHeader(client.getInputStream());
            String[] request = header.split("\r\n", 2)[0].split(" ");
            if (request.length != 3 || !"CONNECT".equals(request[0])) throw new IOException();
            URI uri = URI.create("https://" + request[1]);
            if (uri.getHost() == null || uri.getPort() != 443 || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().isEmpty()) throw new IOException();
            InetAddress[] ips = resolver.resolver(uri.getHost());
            if (ips.length == 0) throw new IOException();
            for (InetAddress ip : ips) if (!esPublica(ip)) throw new IOException();
            upstream = new Socket(); sockets.add(upstream);
            upstream.connect(new InetSocketAddress(ips[0], 443), 5000);
            upstream.setSoTimeout(45000);
            client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            Socket remote = upstream;
            workers.execute(() -> copy(remote, client));
            copy(client, remote);
        } catch (Exception e) {
            try { client.getOutputStream().write("HTTP/1.1 403 Forbidden\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII)); }
            catch (IOException ignored) { }
        } finally { closeSocket(client); closeSocket(upstream); }
    }
    private void copy(Socket source, Socket destination) {
        try { source.getInputStream().transferTo(destination.getOutputStream()); }
        catch (IOException ignored) { }
        finally { closeSocket(source); closeSocket(destination); }
    }
    private static String readHeader(InputStream input) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int b, end = 0;
        byte[] terminator = {13, 10, 13, 10};
        while (header.size() < 8192 && (b = input.read()) != -1) {
            header.write(b);
            end = b == terminator[end] ? end + 1 : (b == 13 ? 1 : 0);
            if (end == 4) return header.toString(StandardCharsets.US_ASCII);
        }
        throw new IOException();
    }
    static boolean esPublica(InetAddress ip) {
        if (ip.isAnyLocalAddress() || ip.isLoopbackAddress() || ip.isLinkLocalAddress()
                || ip.isSiteLocalAddress() || ip.isMulticastAddress()) return false;
        byte[] bytes = ip.getAddress();
        int first = bytes[0] & 255, second = bytes[1] & 255, third = bytes[2] & 255;
        if (bytes.length == 4) {
            return first != 0 && first < 224 && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && (second == 0 || (second == 88 && third == 99)))
                    && !(first == 198 && (second == 18 || second == 19 || (second == 51 && third == 100)))
                    && !(first == 203 && second == 0 && third == 113);
        }
        // Only native global unicast; reject translation/tunnelling and documentation prefixes.
        return (first & 0xe0) == 0x20 && !(first == 0x20 && second == 0x02)
                && !(first == 0x20 && second == 0x01 && (third < 2 || (third == 0x0d && (bytes[3] & 255) == 0xb8)));
    }
    private void closeSocket(Socket socket) {
        if (socket == null) return;
        sockets.remove(socket);
        try { socket.close(); } catch (IOException ignored) { }
    }
    @Override public void close() {
        closed = true;
        try { server.close(); } catch (IOException ignored) { }
        sockets.forEach(this::closeSocket); workers.shutdownNow();
    }
}
