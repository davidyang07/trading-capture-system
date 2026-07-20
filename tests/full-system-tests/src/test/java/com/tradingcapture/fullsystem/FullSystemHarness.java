package com.tradingcapture.fullsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Thin client for driving the LIVE full stack from the cross-service scenario tests: mints real
 * Keycloak tokens, calls the service REST APIs over HTTP, and produces/consumes Kafka records.
 * Everything is env/system-property configurable with localhost defaults matching
 * docker-compose.full.yml + scripts/full-stack-up.ps1. No credential is hardcoded — dev passwords
 * are read from the committed realm export (development placeholders) unless overridden by env.
 */
final class FullSystemHarness {

    static final String TRADING = env("FS_TRADING_URL", "http://localhost:8081");
    static final String ACCOUNT = env("FS_ACCOUNT_URL", "http://localhost:8082");
    static final String ENRICHMENT = env("FS_ENRICHMENT_URL", "http://localhost:8083");
    static final String KEYCLOAK = env("FS_KEYCLOAK_URL", "http://localhost:8180");
    static final String REALM = env("FS_REALM", "tcs");
    static final String KAFKA = env("FS_KAFKA_BOOTSTRAP", "localhost:9092");
    static final String SCHEMA_REGISTRY = env("FS_SCHEMA_REGISTRY", "http://localhost:8085");
    static final String INBOUND_TOPIC = "tcs.fix.allocation-instruction.v1";

    private static final String DEV_CLI_CLIENT = env("FS_DEV_CLI_CLIENT", "tcs-dev-cli");
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    // --- Tokens -----------------------------------------------------------------------------

    /** A user access token via the dev direct-grant client (tcs-dev-cli). */
    String userToken(String username) {
        String password = env("FS_PASSWORD_" + username.toUpperCase().replace('-', '_'),
                realmPassword(username));
        return tokenRequest(Map.of(
                "grant_type", "password",
                "client_id", DEV_CLI_CLIENT,
                "username", username,
                "password", password,
                "scope", "openid"));
    }

    private String tokenRequest(Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b).orElse("");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(KEYCLOAK + "/realms/" + REALM + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Token request failed: " + response.statusCode()
                        + " " + response.body());
            }
            return JSON.readTree(response.body()).get("access_token").asText();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Token request failed", e);
        }
    }

    private static String realmPassword(String username) {
        Path realm = Path.of(env("FS_REALM_EXPORT",
                "../../infrastructure/docker/keycloak/tcs-realm.json"));
        try {
            JsonNode root = JSON.readTree(Files.readString(realm));
            for (JsonNode user : root.path("users")) {
                if (username.equals(user.path("username").asText())) {
                    for (JsonNode cred : user.path("credentials")) {
                        if ("password".equals(cred.path("type").asText())) {
                            return cred.path("value").asText();
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the dev realm export at " + realm, e);
        }
        throw new IllegalStateException("No dev password for " + username + " in the realm export");
    }

    // --- REST -------------------------------------------------------------------------------

    record Response(int status, JsonNode body) {
        JsonNode at(String pointer) {
            return body.at(pointer);
        }
    }

    Response get(String url, String token) {
        return send("GET", url, token, null);
    }

    Response post(String url, String token, Object body) {
        return send("POST", url, token, body);
    }

    Response put(String url, String token, Object body) {
        return send("PUT", url, token, body);
    }

    Response delete(String url, String token) {
        return send("DELETE", url, token, null);
    }

    private Response send(String method, String url, String token, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json");
            if (token != null) {
                builder.header("Authorization", "Bearer " + token);
            }
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body));
            if (body != null) {
                builder.header("Content-Type", "application/json");
            }
            HttpRequest request = builder.method(method, publisher).build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode parsed = response.body() == null || response.body().isBlank()
                    ? JSON.createObjectNode()
                    : JSON.readTree(response.body());
            return new Response(response.statusCode(), parsed);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException(method + " " + url + " failed", e);
        }
    }

    // --- Kafka ------------------------------------------------------------------------------

    /** Produces a raw FIX string onto the inbound topic (keyed by AllocID for partition order). */
    void produceFix(String key, String fixPayload) {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(INBOUND_TOPIC, key, fixPayload));
            producer.flush();
        }
    }

    /**
     * Polls an Avro-serialized outbound topic (from the earliest offset, with a fresh group and
     * unique per-run AllocID matching, so no event is missed) until {@code match} accepts a record
     * or the timeout elapses. Returns matching records as generic {@link Object} (the
     * event-contracts SpecificRecord); scenarios assert on the fields they care about.
     */
    List<Object> awaitEvents(String topic, Duration timeout, Predicate<Object> match) {
        return pollEvents(topic, timeout, match, true);
    }

    /** Drains the topic for the whole window and returns every match (used to count duplicates). */
    List<Object> collectEvents(String topic, Duration window, Predicate<Object> match) {
        return pollEvents(topic, window, match, false);
    }

    private List<Object> pollEvents(String topic, Duration timeout, Predicate<Object> match,
                                    boolean returnOnFirst) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "fs-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                "io.confluent.kafka.serializers.KafkaAvroDeserializer");
        props.put("schema.registry.url", SCHEMA_REGISTRY);
        props.put("specific.avro.reader", "true");
        // earliest + a fresh group + unique per-run AllocID matching removes any subscribe/produce
        // race: the outbound event is always found regardless of when the consumer is assigned.
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        List<Object> matches = new ArrayList<>();
        Instant deadline = Instant.now().plus(timeout);
        try (KafkaConsumer<String, Object> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            while (Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, Object> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, Object> record : records) {
                    if (record.value() != null && match.test(record.value())) {
                        matches.add(record.value());
                    }
                }
                if (returnOnFirst && !matches.isEmpty()) {
                    return matches;
                }
            }
        }
        return matches;
    }

    private static String env(String name, String fallback) {
        String value = System.getProperty(name, System.getenv(name));
        return value == null || value.isBlank() ? fallback : value;
    }
}
