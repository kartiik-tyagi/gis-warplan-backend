package com.gis.warplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.gis.warplan.config.WarplanProperties;
import com.gis.warplan.domain.AppUser;
import com.gis.warplan.repository.AppUserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:logincontext;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@SuppressWarnings({"unchecked", "rawtypes"})
class LoginContextTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private WarplanProperties properties;

    @Test
    void loginContextIsPublicAndListsOnlyActiveUsersWithDefaultTheme() {
        AppUser inactive = new AppUser();
        inactive.setName("INACTIVE_USER");
        inactive.setPasswordHash(passwordEncoder.encode("123"));
        inactive.setActive(false);
        users.save(inactive);

        ResponseEntity<Map> response = rest.getForEntity("/api/v1/auth/login-context", Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = response.getBody();
        assertThat(body.get("applicationName")).isEqualTo(properties.getApplicationName());
        assertThat(body.get("exerciseTitle")).isEqualTo(properties.getApplicationName());

        List<Map> activeUsers = (List<Map>) body.get("activeUsers");
        assertThat(activeUsers).isNotEmpty();
        assertThat(activeUsers.stream().map(user -> user.get("name"))).contains("APEX");
        assertThat(activeUsers.stream().map(user -> user.get("name"))).doesNotContain("INACTIVE_USER");

        Map theme = (Map) body.get("loginTheme");
        assertThat(theme.get("defaultTheme")).isEqualTo(true);
        assertThat(theme.get("name")).isEqualTo("Blue");
        Map config = (Map) theme.get("configJson");
        assertThat(config.get("paletteId")).isEqualTo("blue");
        assertThat(config.get("primary")).isNotNull();
    }
}
