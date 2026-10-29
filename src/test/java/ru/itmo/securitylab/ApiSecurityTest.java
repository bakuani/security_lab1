package ru.itmo.securitylab;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ApiSecurityTest {
    private static final String SECRET = HexFormat.of().formatHex(new SecureRandom().generateSeed(48));
    private static final String ALICE_PASSWORD = "Alice-test-only-2026!";
    private static final String BOB_PASSWORD = "Bob-test-only-2026!";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("lab.jwt-secret", () -> SECRET);
        registry.add("lab.alice-password", () -> ALICE_PASSWORD);
        registry.add("lab.bob-password", () -> BOB_PASSWORD);
    }

    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    private String aliceToken;
    private long aliceTask;
    private long bobTask;

    @BeforeEach
    void loginAndReset() throws Exception {
        aliceToken = json.readTree(login("alice", ALICE_PASSWORD).getBody()).get("accessToken").asText();
        aliceTask = jdbc.queryForObject("SELECT MIN(id) FROM task WHERE owner_id = (SELECT id FROM app_user WHERE username = ?)", Long.class, "alice");
        bobTask = jdbc.queryForObject("SELECT MIN(id) FROM task WHERE owner_id = (SELECT id FROM app_user WHERE username = ?)", Long.class, "bob");
        jdbc.update("UPDATE task SET status = ?", "OPEN");
    }

    @Test
    void loginIssuesShortLivedJwtAndNeverReturnsPassword() throws Exception {
        var response = login("alice", ALICE_PASSWORD);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asLong()).isEqualTo(900);
        var jwt = SignedJWT.parse(body.get("accessToken").asText());
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo(SecurityConfig.ISSUER);
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(SecurityConfig.AUDIENCE);
        assertThat(jwt.getJWTClaimsSet().getExpirationTime().getTime()
                - jwt.getJWTClaimsSet().getIssueTime().getTime()).isEqualTo(900_000);
        assertThat(response.getBody()).doesNotContain(ALICE_PASSWORD, "passwordHash");
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-password", "unknown-user", "sqli", "xss"})
    void rejectsInvalidCredentialsWithoutReflectingInput(String kind) {
        String username = switch (kind) {
            case "unknown-user" -> "nobody";
            case "sqli" -> "alice' OR '1'='1' --";
            case "xss" -> "<script>alert(1)</script>";
            default -> "alice";
        };
        var response = login(username, "incorrect-password");
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody()).isEqualTo("{\"error\":\"Invalid credentials\"}");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer garbage", "Basic YWxpY2U6YWJj"})
    void bothProtectedMethodsRequireBearerToken(String authorization) {
        var headers = new HttpHeaders();
        if (!authorization.isEmpty()) headers.set("Authorization", authorization);
        headers.setContentType(MediaType.APPLICATION_JSON);
        for (var response : new ResponseEntity<?>[] {
                http.exchange("/api/data", HttpMethod.GET, new HttpEntity<>(headers), String.class),
                http.exchange("/api/data/" + aliceTask + "/status", HttpMethod.PATCH,
                        new HttpEntity<>(Map.of("status", "DONE"), headers), String.class)}) {
            assertThat(response.getStatusCode().value()).isEqualTo(401);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"tampered", "expired", "issuer", "audience", "missing-audience",
            "missing-expiry", "missing-issued", "future-issued", "subject", "unsigned", "algorithm", "long-lifetime"})
    void rejectsInvalidJwt(String kind) throws Exception {
        Instant now = Instant.now();
        var builder = new JWTClaimsSet.Builder().subject(kind.equals("subject") ? "alice" : "1")
                .issuer(kind.equals("issuer") ? "attacker" : SecurityConfig.ISSUER)
                .audience(kind.equals("audience") ? "other-api" : SecurityConfig.AUDIENCE)
                .issueTime(Date.from(now.minusSeconds(60))).expirationTime(Date.from(now.plusSeconds(600)));
        switch (kind) {
            case "expired" -> builder.issueTime(Date.from(now.minusSeconds(1200))).expirationTime(Date.from(now.minusSeconds(300)));
            case "missing-audience" -> builder.audience((String) null);
            case "missing-expiry" -> builder.expirationTime(null);
            case "missing-issued" -> builder.issueTime(null);
            case "future-issued" -> builder.issueTime(Date.from(now.plusSeconds(60)));
            case "long-lifetime" -> builder.expirationTime(Date.from(now.plusSeconds(3600)));
            default -> { }
        }
        String token;
        if (kind.equals("unsigned")) {
            token = new PlainJWT(builder.build()).serialize();
        } else {
            var jwt = new SignedJWT(new JWSHeader(kind.equals("algorithm") ? JWSAlgorithm.HS512 : JWSAlgorithm.HS256), builder.build());
            jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
            token = jwt.serialize();
            if (kind.equals("tampered")) {
                int start = token.lastIndexOf('.') + 1;
                token = token.substring(0, start) + (token.charAt(start) == 'A' ? 'B' : 'A') + token.substring(start + 1);
            }
        }
        assertThat(get(token).getStatusCode().value()).isEqualTo(401);
        assertThat(patch(aliceTask, token, Map.of("status", "DONE")).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void listIsScopedToOwnerAndEscapesAllTextFields() throws Exception {
        var response = get(aliceToken);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode tasks = json.readTree(response.getBody());
        assertThat(tasks.size()).isEqualTo(2);
        assertThat(response.getBody()).doesNotContain("<script>", "<img", "Задача Bob", "ownerId", "password");
        assertThat(tasks.get(1).get("title").asText()).contains("&lt;script&gt;", "&#39;");
        assertThat(tasks.get(1).get("description").asText()).contains("&lt;img");
        assertThat(jdbc.queryForObject("SELECT title FROM task WHERE id = ?", String.class, tasks.get(1).get("id").asLong()))
                .contains("<script>");
    }

    @Test
    void canChangeOwnStatus() throws Exception {
        var response = patch(aliceTask, aliceToken, Map.of("status", "DONE"));
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(get(aliceToken).getBody()).get(0).get("status").asText()).isEqualTo("DONE");
    }

    @Test
    void cannotChangeSomeoneElsesTaskOrForgeOwner() throws Exception {
        var foreign = patch(bobTask, aliceToken, Map.of("status", "DONE"));
        var missing = patch(999999L, aliceToken, Map.of("status", "DONE"));
        assertThat(foreign.getStatusCode().value()).isEqualTo(404);
        assertThat(foreign.getBody()).isEqualTo(missing.getBody());
        assertThat(patch(bobTask, aliceToken, Map.of("status", "DONE", "ownerId", 2)).getStatusCode().value()).isEqualTo(400);
        String bobToken = json.readTree(login("bob", BOB_PASSWORD).getBody()).get("accessToken").asText();
        JsonNode bobTasks = json.readTree(get(bobToken).getBody());
        assertThat(bobTasks.size()).isEqualTo(1);
        assertThat(bobTasks.get(0).get("status").asText()).isEqualTo("OPEN");
    }

    @ParameterizedTest
    @ValueSource(strings = {"INVALID", "<script>alert(1)</script>", "DONE'; DROP TABLE task; --", ""})
    void rejectsInvalidStatus(String value) {
        var response = patch(aliceTask, aliceToken, Map.of("status", value));
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo("{\"error\":\"Invalid request\"}");
    }

    @Test
    void rejectsInvalidIdNullMissingFieldsAndUnknownJsonProperties() {
        assertThat(patch(0, aliceToken, Map.of("status", "DONE")).getStatusCode().value()).isEqualTo(400);
        assertThat(patch(aliceTask, aliceToken, Map.of()).getStatusCode().value()).isEqualTo(400);
        assertThat(login("", ALICE_PASSWORD).getStatusCode().value()).isEqualTo(400);
        assertThat(login("alice", "я".repeat(40)).getStatusCode().value()).isEqualTo(400);
        assertThat(http.postForEntity("/auth/login", Map.of("username", "alice", "password", ALICE_PASSWORD,
                "role", "ADMIN"), String.class).getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void responsesHaveSecurityHeadersAndNoSessionCookies() {
        for (var response : new ResponseEntity<?>[] {get(aliceToken), get("invalid")}) {
            assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
            assertThat(response.getHeaders().getFirst("Content-Security-Policy")).contains("default-src 'none'");
            assertThat(response.getHeaders().getFirst("Cache-Control")).contains("no-store");
            assertThat(response.getHeaders().getFirst("Set-Cookie")).isNull();
        }
    }

    @Test
    void databaseStoresSaltedBcryptHashes() {
        for (String username : new String[] {"alice", "bob"}) {
            String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username = ?", String.class, username);
            String password = username.equals("alice") ? ALICE_PASSWORD : BOB_PASSWORD;
            assertThat(hash).startsWith("$2a$12$");
            assertThat(passwordEncoder.matches(password, hash)).isTrue();
            assertThat(hash).isNotEqualTo(passwordEncoder.encode(password));
        }
    }

    @Test
    void deniesUnlistedRoutesAndH2Console() {
        assertThat(http.exchange("/h2-console", HttpMethod.GET, new HttpEntity<>(headers(aliceToken)), String.class)
                .getStatusCode().value()).isEqualTo(403);
        assertThat(http.exchange("/api/data", HttpMethod.POST, new HttpEntity<>(Map.of(), headers(aliceToken)), String.class)
                .getStatusCode().value()).isEqualTo(403);
    }

    private ResponseEntity<String> login(String username, String password) {
        return http.postForEntity("/auth/login", Map.of("username", username, "password", password), String.class);
    }
    private ResponseEntity<String> get(String token) {
        return http.exchange("/api/data", HttpMethod.GET, new HttpEntity<>(headers(token)), String.class);
    }
    private ResponseEntity<String> patch(long id, String token, Map<String, ?> body) {
        return http.exchange("/api/data/" + id + "/status", HttpMethod.PATCH, new HttpEntity<>(body, headers(token)), String.class);
    }
    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
