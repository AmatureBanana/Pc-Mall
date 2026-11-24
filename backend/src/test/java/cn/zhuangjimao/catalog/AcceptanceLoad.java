package cn.zhuangjimao.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

final class AcceptanceLoad implements AutoCloseable {
    static final int WARMUP_WAVES = 3;
    static final int TIMED_WAVES = 10;
    private final List<Session> sessions = new ArrayList<>();
    private final List<String> samples = new ArrayList<>();
    private final List<String> summary = new ArrayList<>();
    private final List<String> missedTargets = new ArrayList<>();
    private final ExecutorService workers = Executors.newFixedThreadPool(20);
    private final ObjectMapper json;
    private final String base;

    AcceptanceLoad(int port, ObjectMapper json) {
        this.json = json;
        base = "http://127.0.0.1:" + port;
        samples.add("operation,phase,wave,worker,elapsed_ns,http_status,result");
        summary.add("operation,concurrency,samples,p95_ms,target_ms,within_target");
    }

    Session session() {
        var session = new Session(HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .connectTimeout(Duration.ofSeconds(5)).build());
        sessions.add(session);
        return session;
    }

    <T> List<T> wave(List<? extends Callable<T>> requests) throws Exception {
        var start = new CyclicBarrier(requests.size());
        var futures = workers.invokeAll(requests.stream().<Callable<T>>map(request -> () -> {
            start.await(15, TimeUnit.SECONDS);
            return request.call();
        }).toList(), 90, TimeUnit.SECONDS);
        var results = new ArrayList<T>(futures.size());
        for (var future : futures) results.add(future.get());
        return results;
    }

    List<Reply> recordWave(String operation, String phase, int wave, List<? extends Callable<Reply>> requests) throws Exception {
        var replies = wave(requests);
        for (int i = 0; i < replies.size(); i++) {
            var reply = replies.get(i);
            samples.add(String.join(",", operation, phase, String.valueOf(wave), String.valueOf(i + 1),
                    String.valueOf(reply.nanos()), String.valueOf(reply.status()), reply.result()));
        }
        return replies;
    }

    void summarize(String operation, int concurrency, List<Reply> replies, long targetMs, boolean required) {
        assertThat(replies).hasSize(concurrency * TIMED_WAVES);
        var durations = replies.stream().map(Reply::nanos).sorted().toList();
        long p95 = durations.get((int) Math.ceil(durations.size() * 0.95) - 1);
        boolean withinTarget = p95 <= TimeUnit.MILLISECONDS.toNanos(targetMs);
        summary.add(String.format(java.util.Locale.ROOT, "%s,%d,%d,%.3f,%d,%b",
                operation, concurrency, durations.size(), p95 / 1_000_000.0, targetMs, withinTarget));
        if (required && !withinTarget) missedTargets.add(operation);
    }

    void assertTargets() { assertThat(missedTargets).as("operations exceeding required P95 targets").isEmpty(); }

    void writeEvidence(Path directory) throws Exception {
        Files.createDirectories(directory);
        Files.write(directory.resolve("requests.csv"), samples);
        Files.write(directory.resolve("summary.csv"), summary);
    }

    @Override public void close() {
        workers.shutdownNow();
        sessions.forEach(session -> session.http.close());
    }

    final class Session {
        private final HttpClient http;
        private String csrf;

        private Session(HttpClient http) { this.http = http; }

        void refreshCsrf() throws Exception {
            csrf = get("/api/auth/csrf").ok().path("token").asText();
            assertThat(csrf).isNotBlank();
        }

        Reply get(String path) throws Exception { return request("GET", path, null); }
        Reply post(String path, Object body) throws Exception { return request("POST", path, body); }
        Reply delete(String path) throws Exception { return request("DELETE", path, null); }

        private Reply request(String method, String path, Object body) throws Exception {
            var builder = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15));
            if (csrf != null) builder.header("X-CSRF-TOKEN", csrf);
            var publisher = HttpRequest.BodyPublishers.noBody();
            if (body != null) {
                builder.header("Content-Type", "application/json");
                publisher = HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body));
            }
            var request = builder.method(method, publisher).build();
            long start = System.nanoTime();
            HttpResponse<String> response;
            try {
                response = http.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (IOException error) {
                return new Reply(path, System.nanoTime() - start, 0,
                        json.createObjectNode().put("code", error.getClass().getSimpleName()));
            }
            long elapsed = System.nanoTime() - start;
            try {
                return new Reply(path, elapsed, response.statusCode(), json.readTree(response.body()));
            } catch (JsonProcessingException error) {
                return new Reply(path, elapsed, response.statusCode(),
                        json.createObjectNode().put("code", "NON_JSON_RESPONSE"));
            }
        }
    }

    record Reply(String path, long nanos, int status, JsonNode body) {
        JsonNode ok() {
            assertThat(status).as("%s returned %s", path, body).isEqualTo(200);
            assertThat(body.hasNonNull("data")).as("%s returned %s", path, body).isTrue();
            return body.path("data");
        }

        String result() {
            var data = body.path("data");
            return body.path("code").asText(data.path("status").asText(data.path("order").path("status").asText()));
        }
    }
}
