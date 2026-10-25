package com.timetables;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import com.timetables.apisncf.HttpTransport;

/**
 * Desktop implementation of the core's HttpTransport, built on java.net.http
 * (not available on Android, which will have its own implementation).
 */
public final class JdkHttpTransport implements HttpTransport {

    // One shared, thread-safe client.
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public Response get(String url, Map<String, String> headers, Duration timeout) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET();
        headers.forEach(request::header);

        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Response(response.statusCode(), response.body());
    }
}
