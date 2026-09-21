package com.ziyadsamhaoui.messagingapigateway.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Stands in for a downstream BadrLink service: every request it receives is recorded so tests can
 * assert exactly what the gateway forwarded (path, headers, body) and how many calls reached the
 * backend.
 *
 * <p>Fixed routes (for example the Auth Service JWKS document) survive {@link #respondWith} overrides.
 */
public final class UpstreamStub {

    private final String name;

    private final MockWebServer server = new MockWebServer();

    private final Queue<RecordedRequest> receivedRequests = new ConcurrentLinkedQueue<>();

    private final Map<String, Function<RecordedRequest, MockResponse>> fixedRoutes = new ConcurrentHashMap<>();

    private volatile Function<RecordedRequest, MockResponse> responder;

    private UpstreamStub(String name) {
        this.name = name;
        this.server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                receivedRequests.add(request);
                String pathOnly = pathOf(request);
                Function<RecordedRequest, MockResponse> fixed = fixedRoutes.get(pathOnly);
                if (fixed != null) {
                    return fixed.apply(request);
                }
                Function<RecordedRequest, MockResponse> current = responder;
                return (current != null) ? current.apply(request) : echo(request);
            }
        });
    }

    public static UpstreamStub start(String name) {
        UpstreamStub stub = new UpstreamStub(name);
        try {
            stub.server.start();
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Cannot start upstream stub '" + name + "'", ex);
        }
        return stub;
    }

    /** @return base URL without a trailing slash, so it can be used verbatim as a route {@code uri} */
    public String baseUrl() {
        return "http://" + this.server.getHostName() + ":" + this.server.getPort();
    }

    public String url(String path) {
        return baseUrl() + path;
    }

    public void onRoute(String path, Function<RecordedRequest, MockResponse> handler) {
        this.fixedRoutes.put(path, handler);
    }

    public void respondWith(Function<RecordedRequest, MockResponse> responder) {
        this.responder = responder;
    }

    public void respondWithJson(int status, String json) {
        respondWith(request -> jsonResponse(status, json));
    }

    public void respondOk() {
        respondWithJson(200, "{\"status\":\"ok\"}");
    }

    public void reset() {
        this.receivedRequests.clear();
        this.responder = null;
    }

    public int requestCount() {
        return this.server.getRequestCount();
    }

    public List<RecordedRequest> drainRequests() {
        List<RecordedRequest> drained = new ArrayList<>();
        RecordedRequest request;
        while ((request = this.receivedRequests.poll()) != null) {
            drained.add(request);
        }
        return drained;
    }

    public RecordedRequest awaitRequest(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            RecordedRequest request = this.receivedRequests.poll();
            if (request != null) {
                return request;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(20);
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting an upstream request", ex);
            }
        }
        throw new AssertionError("No request reached the '" + this.name + "' upstream within " + timeout);
    }

    public void shutdown() {
        try {
            this.server.shutdown();
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Cannot stop upstream stub '" + this.name + "'", ex);
        }
    }

    private MockResponse echo(RecordedRequest request) {
        String body = "{\"upstream\":\"" + this.name + "\",\"path\":\"" + pathOf(request) + "\"}";
        return jsonResponse(200, body);
    }

    public static MockResponse jsonResponse(int status, String json) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(json);
    }

    private static String pathOf(RecordedRequest request) {
        String path = request.getPath();
        int queryIndex = path.indexOf('?');
        return (queryIndex >= 0) ? path.substring(0, queryIndex) : path;
    }
}
