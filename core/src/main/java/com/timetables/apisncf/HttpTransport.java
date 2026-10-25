package com.timetables.apisncf;

import java.time.Duration;
import java.util.Map;

/**
 * The only way the core module talks to the network: one HTTP GET.
 *
 * The core is pure Java and must not depend on a particular HTTP library, because the
 * desktop app (java.net.http) and the Android app (HttpURLConnection, OkHttp...) do not
 * share one. Each front-end provides its own implementation; tests provide a fake one.
 */
public interface HttpTransport {

    /** Status code and body of an HTTP response. */
    record Response(int statusCode, String body) {
    }

    /**
     * Performs a GET request. Returns the response whatever its status code; throws
     * only when no response could be obtained (no network, timeout...).
     */
    Response get(String url, Map<String, String> headers, Duration timeout) throws Exception;
}
