package com.gis.warplan;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FoundationRunningTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void healthInfoAndSwaggerRespond() {
        ResponseEntity<String> health = rest.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
        assertThat(health.getHeaders().getFirst("X-Request-Id")).isNotBlank();

        ResponseEntity<String> info = rest.getForEntity("/actuator/info", String.class);
        assertThat(info.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(info.getBody()).contains("JAYANT");

        ResponseEntity<String> swagger = rest.getForEntity("/swagger-ui.html", String.class);
        assertThat(swagger.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(swagger.getBody()).contains("swagger");

        ResponseEntity<String> document = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(document.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(document.getBody()).contains("\"openapi\"");
    }

    @Test
    void unknownPathAndUnexpectedFailureStaySafe() {
        ResponseEntity<String> missing = rest.exchange("/api/v1/missing", HttpMethod.GET, authed(null), String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("NOT_FOUND");
        assertThat(missing.getBody()).contains("requestId");

        ResponseEntity<String> broken = rest.exchange("/api/v1/foundation-check/broken", HttpMethod.GET, authed(null), String.class);
        assertThat(broken.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(broken.getBody()).contains("INTERNAL");
        assertThat(broken.getBody()).doesNotContain("secret");
    }

    @Test
    void invalidAndUnreadableBodiesAreRejected() {
        ResponseEntity<String> invalid = rest.postForEntity(
                "/api/v1/foundation-check/names",
                authed("{\"name\":\"\"}"),
                String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).contains("VALIDATION");
        assertThat(invalid.getBody()).contains("name");

        ResponseEntity<String> unreadable = rest.postForEntity(
                "/api/v1/foundation-check/names",
                authed("{"),
                String.class);
        assertThat(unreadable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unreadable.getBody()).contains("BAD_REQUEST");
        assertThat(unreadable.getBody()).doesNotContain("Unexpected");
    }

    @Test
    void passwordEncoderIsReady() {
        String encoded = passwordEncoder.encode("123");
        assertThat(passwordEncoder.matches("123", encoded)).isTrue();
        assertThat(encoded).doesNotContain("123");
    }

    @Test
    void protectedPathsRequireAuthentication() {
        ResponseEntity<String> anonymous = rest.getForEntity("/api/v1/missing", String.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody()).contains("UNAUTHORIZED");
    }

    private HttpEntity<String> authed(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token());
        return new HttpEntity<>(body, headers);
    }

    private String token() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<java.util.Map> login = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>("{\"name\":\"APEX\",\"password\":\"123\"}", headers), java.util.Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("token");
    }
}
