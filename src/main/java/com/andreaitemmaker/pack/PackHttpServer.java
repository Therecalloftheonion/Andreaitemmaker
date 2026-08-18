package com.andreaitemmaker.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Tiny embedded HTTP server that serves the generated resource pack at {@code /pack.zip},
 * so servers without an external file host still get a working download URL.
 *
 * <p>Only the fixed {@code /pack.zip} path is ever served — the handler never maps request
 * paths to files, so there is no path traversal or arbitrary file exposure. The pack bytes
 * are streamed from the in-memory pack state (already held for SHA-1/upload) in chunks,
 * with a bounded worker pool so a request flood cannot spawn unbounded threads.
 */
public final class PackHttpServer {

    private static final int CHUNK_SIZE = 8192;

    private final Logger logger;
    private final Supplier<byte[]> packSupplier;
    private final Supplier<String> sha1Supplier;

    private HttpServer server;
    private ExecutorService executor;
    private int port = -1;

    public PackHttpServer(Logger logger, Supplier<byte[]> packSupplier, Supplier<String> sha1Supplier) {
        this.logger = logger;
        this.packSupplier = packSupplier;
        this.sha1Supplier = sha1Supplier;
    }

    /**
     * Start (or restart, when the port changed) the server on the given port.
     *
     * @return true when the server is running on {@code port}
     */
    public synchronized boolean start(int port) {
        if (server != null && this.port == port) {
            return true; // already serving on this port; bytes are read live via the supplier
        }
        stop();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handle);
            ThreadPoolExecutor pool = new ThreadPoolExecutor(
                    1, 4, 60, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(32),
                    r -> {
                        Thread t = new Thread(r, "aitem-pack-http");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.CallerRunsPolicy());
            pool.allowCoreThreadTimeOut(true);
            executor = pool;
            server.setExecutor(executor);
            server.start();
            this.port = port;
            logger.info("Serving resource pack on port " + port + " (http://<server-ip>:" + port + "/pack.zip)");
            return true;
        } catch (IOException e) {
            logger.warning("Could not start pack HTTP server on port " + port + ": " + e.getMessage()
                    + " (is the port already in use?)");
            server = null;
            executor = null;
            this.port = -1;
            return false;
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    /** The actual bound port (useful when started on port 0 in tests). */
    public synchronized int getPort() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    /** Stop the server, giving in-flight downloads a short grace period. */
    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        port = -1;
    }

    private void handle(HttpExchange exchange) {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                sendEmpty(exchange, 405);
                return;
            }
            if (!"/pack.zip".equals(exchange.getRequestURI().getPath())) {
                sendEmpty(exchange, 404);
                return;
            }
            byte[] bytes = packSupplier.get();
            if (bytes == null || bytes.length == 0) {
                sendEmpty(exchange, 404); // pack not generated yet
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            String sha1 = sha1Supplier.get();
            if (sha1 != null && !sha1.isEmpty()) {
                String etag = "\"" + sha1 + "\"";
                exchange.getResponseHeaders().set("ETag", etag);
                String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
                if (ifNoneMatch != null && ifNoneMatch.contains(etag)) {
                    exchange.getResponseHeaders().remove("Content-Length");
                    exchange.sendResponseHeaders(304, -1);
                    return;
                }
            }
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                int offset = 0;
                while (offset < bytes.length) {
                    int n = Math.min(CHUNK_SIZE, bytes.length - offset);
                    os.write(bytes, offset, n);
                    offset += n;
                }
            }
        } catch (IOException e) {
            logger.fine("Pack HTTP request failed: " + e.getMessage());
            try {
                sendEmpty(exchange, 500);
            } catch (IOException ignored) {
                // Client already disconnected; there is nothing more to send.
            }
        } finally {
            exchange.close();
        }
    }

    private static void sendEmpty(HttpExchange exchange, int code) throws IOException {
        exchange.sendResponseHeaders(code, -1);
    }
}
