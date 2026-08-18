package com.andreaitemmaker;

import com.andreaitemmaker.pack.PackHttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackHttpServerTest {

    private static final Logger LOG = Logger.getLogger("PackHttpServerTest");

    private final AtomicReference<byte[]> packBytes = new AtomicReference<>();
    private final AtomicReference<String> sha1 = new AtomicReference<>();
    private PackHttpServer server;
    private HttpClient client;

    @BeforeEach
    void setUp() {
        packBytes.set(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10});
        sha1.set("abc123");
        server = new PackHttpServer(LOG, packBytes::get, sha1::get);
        assertTrue(server.start(0), "server must start on an ephemeral port");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private String url() {
        return "http://127.0.0.1:" + server.getPort() + "/pack.zip";
    }

    private HttpResponse<byte[]> get(String path, String headerName, String headerValue) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(
                        "http://127.0.0.1:" + server.getPort() + path))
                .timeout(Duration.ofSeconds(5))
                .GET();
        if (headerName != null) {
            builder.header(headerName, headerValue);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    void servesPackWithCorrectHeadersAndBody() throws Exception {
        HttpResponse<byte[]> response = get("/pack.zip", null, null);
        assertEquals(200, response.statusCode());
        assertEquals("application/zip", response.headers().firstValue("Content-Type").orElse(""));
        assertEquals("10", response.headers().firstValue("Content-Length").orElse(""));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, response.body());
    }

    @Test
    void onlyServesPackZip() throws Exception {
        assertEquals(404, get("/", null, null).statusCode());
        assertEquals(404, get("/other", null, null).statusCode());
        assertEquals(404, get("/pack.zip/extra", null, null).statusCode());
        assertEquals(404, get("/../config.yml", null, null).statusCode());
        // Query strings do not change the resource: the path must still match exactly.
        assertEquals(200, get("/pack.zip?x=1", null, null).statusCode());
    }

    @Test
    void rejectsNonGetMethods() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url()))
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(405, response.statusCode());
    }

    @Test
    void returns304WhenNotModified() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url()))
                .timeout(Duration.ofSeconds(5))
                .header("If-None-Match", "\"abc123\"")
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(304, response.statusCode());
        assertEquals(0, response.body().length);
    }

    @Test
    void mismatchedEtagServesFreshPack() throws Exception {
        HttpResponse<byte[]> response = get("/pack.zip", "If-None-Match", "\"old-etag\"");
        assertEquals(200, response.statusCode());
        assertEquals("\"abc123\"", response.headers().firstValue("ETag").orElse(""));
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}, response.body());
    }

    @Test
    void servesLatestPackAfterSwap() throws Exception {
        // Simulates an async generation publishing a new pack: the server must serve the
        // new bytes immediately without a restart (atomic supplier read).
        byte[] newPack = new byte[]{9, 9, 9, 9};
        sha1.set("new-sha");
        packBytes.set(newPack);
        HttpResponse<byte[]> response = get("/pack.zip", null, null);
        assertEquals(200, response.statusCode());
        assertArrayEquals(newPack, response.body());
        assertEquals("\"new-sha\"", response.headers().firstValue("ETag").orElse(""));
    }

    @Test
    void notGeneratedYetReturns404() throws Exception {
        packBytes.set(null);
        assertEquals(404, get("/pack.zip", null, null).statusCode());
    }

    @Test
    void stopMakesServerUnavailable() {
        server.stop();
        assertFalse(server.isRunning());
        // Binding the same port again must work after stop (no leaked resources).
        assertTrue(server.start(0));
    }
}
