package com.andreaitemmaker.pack;

import com.andreaitemmaker.config.PluginConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Uploads the generated pack zip to an external host (CDN, file server, etc.) via HTTP. */
public final class PackUploader {

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Upload {@code bytes} according to the configured method/url/headers and return the
     * public URL players should use.
     *
     * @throws IOException when the upload fails (network or non-2xx response)
     */
    public String upload(PluginConfig.Pack config, byte[] bytes) throws IOException {
        String url = config.uploadUrl;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/zip")
                .header("User-Agent", "Andreaitemmaker");
        for (Map.Entry<String, String> header : config.uploadHeaders.entrySet()) {
            builder.header(header.getKey(), header.getValue());
        }
        HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.ofByteArray(bytes);
        HttpRequest request = "POST".equalsIgnoreCase(config.uploadMethod)
                ? builder.POST(body).build()
                : builder.PUT(body).build();
        try {
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IOException("upload returned HTTP " + status);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("upload interrupted", e);
        }
        String publicUrl = config.uploadPublicUrl;
        return publicUrl != null && !publicUrl.isEmpty() ? publicUrl : url;
    }
}
