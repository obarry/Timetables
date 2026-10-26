package com.timetables.android;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.timetables.apisncf.HttpTransport;

/**
 * Android implementation of the core's HttpTransport, built on HttpURLConnection
 * (java.net.http does not exist on Android). Must be called from a background thread.
 */
final class AndroidHttpTransport implements HttpTransport {

    private static final int MAX_CONNECT_TIMEOUT_MS = 10_000;

    @Override
    public Response get(String url, Map<String, String> headers, Duration timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            int timeoutMs = (int) timeout.toMillis();
            connection.setConnectTimeout(Math.min(timeoutMs, MAX_CONNECT_TIMEOUT_MS));
            connection.setReadTimeout(timeoutMs);
            connection.setRequestMethod("GET");
            for (Map.Entry<String, String> header : headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }

            int status = connection.getResponseCode();
            // On an error status the body (if any) is in the error stream.
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = stream == null ? "" : readAll(stream);
            return new Response(status, body);
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream stream) throws IOException {
        try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
