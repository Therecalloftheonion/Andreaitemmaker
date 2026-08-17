package com.andreaitemmaker.pack;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Tiny embedded HTTP server that serves the generated resource pack at {@code /pack.zip},
 * so servers without an external file host still get a working download URL.
 */
public final class PackHttpServer {

    private final Logger logger;
    private HttpServer server;
    private ExecutorService executor;
    private File packFile;

    public PackHttpServer(Logger logger) {
        this.logger = logger;
    }

    /** Start (or restart) the server on the given port serving the given file. */
    public synchronized boolean start(int port, File file) {
        stop();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", this::handle);
            executor = Executors.newCachedThreadPool();
            server.setExecutor(executor);
            server.start();
            packFile = file;
            logger.info("Serving resource pack on port " + port + " (http://<server-ip>:" + port + "/pack.zip)");
            return true;
        } catch (IOException e) {
            logger.warning("Could not start pack HTTP server on port " + port + ": " + e.getMessage()
                    + " (is the port already in use?)");
            server = null;
            return false;
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                    || !"/pack.zip".equals(exchange.getRequestURI().getPath())) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            File file = packFile;
            if (file == null || !file.isFile()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            logger.fine("Pack HTTP request failed: " + e.getMessage());
        } finally {
            exchange.close();
        }
    }
}
