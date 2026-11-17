package com.drdo.jayant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
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
        assertThat(info.getBody()).contains("Jayant");

        ResponseEntity<String> swagger = rest.getForEntity("/swagger-ui.html", String.class);
        assertThat(swagger.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(swagger.getBody()).contains("swagger");

        ResponseEntity<String> document = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(document.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(document.getBody()).contains("\"openapi\"");
    }

    @Test
    void unknownPathAndUnexpectedFailureStaySafe() {
        ResponseEntity<String> missing = rest.getForEntity("/api/v1/missing", String.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).contains("NOT_FOUND");
        assertThat(missing.getBody()).contains("requestId");

        ResponseEntity<String> broken = rest.getForEntity("/api/v1/foundation-check/broken", String.class);
        assertThat(broken.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(broken.getBody()).contains("INTERNAL");
        assertThat(broken.getBody()).doesNotContain("secret");
    }

    @Test
    void invalidAndUnreadableBodiesAreRejected() {
        ResponseEntity<String> invalid = rest.postForEntity(
                "/api/v1/foundation-check/names",
                json("{\"name\":\"\"}"),
                String.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody()).contains("VALIDATION");
        assertThat(invalid.getBody()).contains("name");

        ResponseEntity<String> unreadable = rest.postForEntity(
                "/api/v1/foundation-check/names",
                json("{"),
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

    private static org.springframework.http.HttpEntity<String> json(String body) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new org.springframework.http.HttpEntity<>(body, headers);
    }
}
