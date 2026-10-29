package ru.itmo.securitylab;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "lab.jwt-secret=test-only-key-0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "lab.anna-password=AnnaDemo2026!", "lab.ivan-password=IvanDemo2026!"})
@AutoConfigureTestRestTemplate
class ApiSecurityTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtEncoder jwtEncoder;

    @Test
    void requiresToken() {
        assertThat(http.getForEntity("/api/data", String.class).getStatusCode().value()).isEqualTo(401);
        var response = http.exchange("/api/data/1/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "DONE")), String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void loginAndListOwnTasks() {
        var login = login("anna", "AnnaDemo2026!");
        assertThat(login.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(login.getBody()).get("expiresIn").asInt()).isEqualTo(900);
        var response = get(token());
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(response.getBody()).size()).isEqualTo(2);
        assertThat(response.getBody()).contains("&lt;script&gt;").doesNotContain("<script>", "<img");
    }

    @Test
    void rejectsWrongPasswordAndSqlInjection() {
        assertThat(login("anna", "wrong-password").getStatusCode().value()).isEqualTo(401);
        assertThat(login("anna' OR '1'='1' --", "wrong-password").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void changesOnlyOwnTask() {
        String token = token();
        assertThat(patch(1, token, "DONE").getStatusCode().value()).isEqualTo(200);
        assertThat(json.readTree(get(token).getBody()).get(0).get("status").asText()).isEqualTo("DONE");
        assertThat(patch(3, token, "DONE").getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void rejectsInvalidStatusAndToken() {
        assertThat(patch(1, token(), "INVALID").getStatusCode().value()).isEqualTo(400);
        assertThat(get("invalid.jwt.token").getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void rejectsExpiredToken() {
        Instant now = Instant.now();
        var claims = JwtClaimsSet.builder().subject("1").issuer(SecurityConfig.ISSUER)
                .audience(List.of(SecurityConfig.AUDIENCE))
                .issuedAt(now.minusSeconds(1200)).expiresAt(now.minusSeconds(300)).build();
        String expired = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThat(get(expired).getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void storesPasswordAsBcryptHash() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username = ?",
                String.class, "anna");
        assertThat(hash).startsWith("$2a$12$");
        assertThat(passwordEncoder.matches("AnnaDemo2026!", hash)).isTrue();
    }

    private ResponseEntity<String> login(String username, String password) {
        return http.postForEntity("/auth/login", Map.of("username", username, "password", password), String.class);
    }

    private String token() {
        return json.readTree(login("anna", "AnnaDemo2026!").getBody()).get("accessToken").asText();
    }

    private ResponseEntity<String> get(String token) {
        return http.exchange("/api/data", HttpMethod.GET, new HttpEntity<>(headers(token)), String.class);
    }

    private ResponseEntity<String> patch(long id, String token, String status) {
        return http.exchange("/api/data/" + id + "/status", HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", status), headers(token)), String.class);
    }

    private HttpHeaders headers(String token) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
