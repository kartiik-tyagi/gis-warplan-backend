package com.gis.warplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.RoleCodes;
import com.gis.warplan.domain.RoleEntity;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.FunctionalityRepository;
import com.gis.warplan.repository.RoleRepository;
import com.gis.warplan.repository.ThemeRepository;
import com.gis.warplan.config.DomainSeedRunner;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@SuppressWarnings({"unchecked", "rawtypes"})
class DomainFoundationTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private FunctionalityRepository functionalities;
    @Autowired
    private ThemeRepository themes;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DomainSeedRunner seedRunner;

    @Test
    void seedIsIdempotentAndComplete() throws Exception {
        seedRunner.run(null);
        seedRunner.run(null);
        assertThat(functionalities.count()).isEqualTo(FunctionalityCodes.ALL.size());
        assertThat(roles.count()).isEqualTo(4);
        assertThat(themes.count()).isEqualTo(17);
        assertThat(themes.findByName("Blue")).isPresent();
        assertThat(themes.findFirstByDefaultThemeTrueOrderByIdAsc()).isPresent();
        assertThat(themes.findFirstByDefaultThemeTrueOrderByIdAsc().orElseThrow().getName()).isEqualTo("Blue");
        assertThat(users.findByName("APEX")).isPresent();
        RoleEntity admin = roles.findByCode(RoleCodes.ADMINISTRATOR).orElseThrow();
        assertThat(admin.getFunctionalityIds()).hasSize(FunctionalityCodes.ALL.size());
        AppUser apex = users.findByName("APEX").orElseThrow();
        assertThat(apex.getRoleIds()).containsExactly(admin.getId());
        assertThat(passwordEncoder.matches("123", apex.getPasswordHash())).isTrue();
    }

    @Test
    void loginMeAndLogout() {
        ResponseEntity<Map> bad = login("APEX", "wrong");
        assertThat(bad.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<Map> ok = login("APEX", "123");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = (String) ok.getBody().get("token");
        Map me = (Map) ok.getBody().get("me");
        assertThat((List<String>) me.get("functionalityCodes")).containsAll(FunctionalityCodes.ALL);
        assertThat((List<String>) me.get("roleCodes")).contains(RoleCodes.ADMINISTRATOR);

        ResponseEntity<Map> fetched = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearer(token, null), Map.class);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("name")).isEqualTo("APEX");

        ResponseEntity<Void> logout = rest.exchange("/api/v1/auth/logout", HttpMethod.POST, bearer(token, null), Void.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        ResponseEntity<String> after = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearer(token, null), String.class);
        assertThat(after.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void usernameAliasWorksAndGarbageTokenIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> ok = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>("{\"username\":\"APEX\",\"password\":\"123\"}", headers), Map.class);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> garbage = rest.exchange("/api/v1/auth/me", HttpMethod.GET, bearer("not.a.token", null), String.class);
        assertThat(garbage.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void iconsListReadAndStaySafe() {
        String token = (String) login("APEX", "123").getBody().get("token");

        ResponseEntity<Map> tree = rest.exchange("/api/v1/icons", HttpMethod.GET, bearer(token, null), Map.class);
        assertThat(tree.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tree.getBody().toString()).contains("circle.svg");

        ResponseEntity<String> content = rest.exchange("/api/v1/icons/content?path=circle.svg", HttpMethod.GET,
                bearer(token, null), String.class);
        assertThat(content.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content.getBody()).contains("<svg");
        assertThat(content.getBody()).contains("var(--wp-primary)");

        for (String path : List.of("..%2Fapplication.yml", "%2e%2e%2f%2e%2e%2fpom.xml", "a%5C..%5Cb.svg", "%2Fetc%2Fpasswd", "C:%2Fwindows%2Fwin.ini")) {
            ResponseEntity<String> attempt = rest.exchange("/api/v1/icons/content?path=" + path, HttpMethod.GET,
                    bearer(token, null), String.class);
            assertThat(attempt.getStatusCode().value()).isIn(400, 404);
        }
    }

    @Test
    void uploadNeedsFunctionalityAndRejectsBadInput() {
        String admin = (String) login("APEX", "123").getBody().get("token");
        RoleEntity commander = roles.findByCode(RoleCodes.COMMANDER).orElseThrow();
        if (users.findByName("viewer").isEmpty()) {
            AppUser viewer = new AppUser();
            viewer.setName("viewer");
            viewer.setPasswordHash(passwordEncoder.encode("pw"));
            viewer.setActive(true);
            viewer.setRoleIds(List.of(commander.getId()));
            users.save(viewer);
        }
        String viewerToken = (String) login("viewer", "pw").getBody().get("token");

        assertThat(upload(viewerToken, "forbidden.svg", validSvg()).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(upload(admin, "evil.svg", "<svg><script>alert(1)</script></svg>").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(admin, "..%2Fx.svg", validSvg()).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(upload(admin, "notes.txt", "hi").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String unique = "uploaded-" + java.util.UUID.randomUUID().toString().substring(0, 8) + ".svg";
        ResponseEntity<String> created = upload(admin, unique, validSvg());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(upload(admin, unique, validSvg()).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void drawnSvgIsStoredRevisedAndThemed() {
        String admin = (String) login("APEX", "123").getBody().get("token");
        RoleEntity commander = roles.findByCode(RoleCodes.COMMANDER).orElseThrow();
        if (users.findByName("viewer").isEmpty()) {
            AppUser viewer = new AppUser();
            viewer.setName("viewer");
            viewer.setPasswordHash(passwordEncoder.encode("pw"));
            viewer.setActive(true);
            viewer.setRoleIds(List.of(commander.getId()));
            users.save(viewer);
        }
        String viewerToken = (String) login("viewer", "pw").getBody().get("token");
        String name = "mark-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">"
                + "<rect x=\"2\" y=\"2\" width=\"8\" height=\"8\" fill=\"var(--wp-primary)\" data-wp-fill=\"primary\"/></svg>";

        assertThat(draw(viewerToken, name, "marks", "HQ mark", svg).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> created = draw(admin, name, "marks", "HQ mark", svg);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String path = (String) created.getBody().get("path");
        assertThat(path).isEqualTo("marks/" + name + ".svg");

        ResponseEntity<String> content = rest.exchange("/api/v1/icons/content?path=" + path, HttpMethod.GET,
                bearer(admin, null), String.class);
        assertThat(content.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content.getBody()).contains("var(--wp-primary)");
        assertThat(content.getBody()).doesNotContain("<script");

        ResponseEntity<Map> assets = rest.exchange("/api/v1/asset-management", HttpMethod.GET, bearer(admin, null), Map.class);
        assertThat(((Map) assets.getBody().get("labels")).get(path)).isEqualTo("HQ mark");
        assertThat(draw(admin, name, "marks", "HQ mark", svg).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        String evil = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>";
        assertThat(draw(admin, name + "-evil", "", "", evil).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String revised = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\">"
                + "<circle cx=\"12\" cy=\"12\" r=\"5\" fill=\"var(--wp-error)\" data-wp-fill=\"error\"/></svg>";
        ResponseEntity<Map> updated = revise(admin, path, "HQ mark", revised);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<String> after = rest.exchange("/api/v1/icons/content?path=" + path, HttpMethod.GET,
                bearer(admin, null), String.class);
        assertThat(after.getBody()).contains("var(--wp-primary)");
        assertThat(after.getBody()).doesNotContain("var(--wp-error)");
    }

    private static String validSvg() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"4\" height=\"4\"><rect width=\"4\" height=\"4\"/></svg>";
    }

    private ResponseEntity<Map> login(String name, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>("{\"name\":\"" + name + "\",\"password\":\"" + password + "\"}", headers), Map.class);
    }

    private ResponseEntity<Map> draw(String token, String name, String directory, String label, String svg) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("directory", directory);
        body.put("label", label);
        body.put("svg", svg);
        return rest.exchange("/api/v1/icons/draw", HttpMethod.POST, json(token, body), Map.class);
    }

    private ResponseEntity<Map> revise(String token, String path, String label, String svg) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("path", path);
        body.put("label", label);
        body.put("svg", svg);
        return rest.exchange("/api/v1/icons/draw", HttpMethod.PUT, json(token, body), Map.class);
    }

    private HttpEntity<Map<String, String>> json(String token, Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }

    private ResponseEntity<String> upload(String token, String fileName, String content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.setBearerAuth(token);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content.getBytes()) {
            @Override
            public String getFilename() {
                return fileName.replace("%2F", "/");
            }
        });
        return rest.exchange("/api/v1/icons", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static HttpEntity<String> bearer(String token, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(body, headers);
    }
}
