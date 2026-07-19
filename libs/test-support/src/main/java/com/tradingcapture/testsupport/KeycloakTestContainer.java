package com.tradingcapture.testsupport;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Singleton Keycloak Testcontainer that imports the <em>real</em> committed {@code tcs} realm
 * ({@code infrastructure/docker/keycloak/tcs-realm.json}) and issues <em>real</em> tokens, so the
 * live-security suite proves the realm's role/scope/audience mappings actually produce the
 * authorities the resource servers rely on — not just that a hand-built token would validate.
 *
 * <p>Image and realm match docker-compose exactly ({@code quay.io/keycloak/keycloak:26.3},
 * {@code start-dev --import-realm}). Like the MySQL/Redis singletons it starts once per JVM and is
 * reaped by Ryuk at exit. A test points its resource server at {@link #issuerUri()} via
 * {@code @DynamicPropertySource} and acquires tokens with {@link #userToken} /
 * {@link #clientCredentialsToken}.</p>
 */
public final class KeycloakTestContainer {

    private static final String REALM = "tcs";
    /** Dev/test-only direct-grant client in the realm export used to mint user tokens. */
    public static final String DEV_CLI = "tcs-dev-cli";

    private static final GenericContainer<?> KEYCLOAK =
            new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:26.3"))
                    .withExposedPorts(8080)
                    .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
                    .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin_dev")
                    .withEnv("KC_HTTP_PORT", "8080")
                    .withCopyFileToContainer(
                            MountableFile.forHostPath(locateRealmFile()),
                            "/opt/keycloak/data/import/tcs-realm.json")
                    .withCommand("start-dev", "--import-realm")
                    // Waiting for the realm's OIDC discovery doc proves both boot AND import.
                    .waitingFor(Wait.forHttp("/realms/" + REALM + "/.well-known/openid-configuration")
                            .forPort(8080)
                            .forStatusCode(200)
                            .withStartupTimeout(Duration.ofMinutes(4)));

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final Pattern ACCESS_TOKEN =
            Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    static {
        KEYCLOAK.start();
    }

    private KeycloakTestContainer() {
    }

    /** Base realm issuer, e.g. {@code http://localhost:32873/realms/tcs}. */
    public static String issuerUri() {
        return baseUrl() + "/realms/" + REALM;
    }

    private static String baseUrl() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    private static String tokenEndpoint() {
        return issuerUri() + "/protocol/openid-connect/token";
    }

    /**
     * Acquires a real user access token via the dev-only direct-grant client (Resource Owner
     * Password). The returned token carries the realm role and user scopes the realm maps for
     * that user — exactly what a browser login would yield.
     */
    public static String userToken(String username, String password) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "password");
        form.put("client_id", DEV_CLI);
        form.put("username", username);
        form.put("password", password);
        return requestToken(form);
    }

    /** Acquires a real machine access token via client-credentials for a service-account client. */
    public static String clientCredentialsToken(String clientId, String clientSecret) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", clientId);
        form.put("client_secret", clientSecret);
        return requestToken(form);
    }

    private static String requestToken(Map<String, String> form) {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenEndpoint()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                // Never echo the request body — it may contain a dev credential.
                throw new IllegalStateException(
                        "Keycloak token request failed with HTTP " + response.statusCode());
            }
            Matcher matcher = ACCESS_TOKEN.matcher(response.body());
            if (!matcher.find()) {
                throw new IllegalStateException("Token response contained no access_token");
            }
            return matcher.group(1);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Keycloak token request errored", e);
        }
    }

    /**
     * Locates the committed realm export by walking up from the module working directory, so the
     * container works from any Maven module without a hardcoded relative path.
     */
    private static Path locateRealmFile() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("infrastructure/docker/keycloak/tcs-realm.json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "Could not locate infrastructure/docker/keycloak/tcs-realm.json from "
                        + Paths.get("").toAbsolutePath());
    }
}
