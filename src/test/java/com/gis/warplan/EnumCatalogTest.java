package com.gis.warplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import org.springframework.test.context.ActiveProfiles;

import com.gis.warplan.domain.Side;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.service.EnumCatalogService;
import com.gis.warplan.service.SideService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:enummgmt;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@SuppressWarnings({"unchecked", "rawtypes"})
class EnumCatalogTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private EnumCatalogService enums;

    @Autowired
    private SideRepository sides;

    @Test
    void listsAreSeededAndEditsAreKept() {
        String token = token("APEX", "123");
        ResponseEntity<List> listed = callList(token, "/api/v1/enums");
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map> lists = listed.getBody();
        assertThat(lists).hasSize(38);
        Map dayNight = lists.stream().filter(row -> "day-night".equals(row.get("key"))).findFirst().orElseThrow();
        assertThat(dayNight.get("label")).isEqualTo("Day / Night");
        assertThat(dayNight.get("sideSpecific")).isEqualTo(false);
        assertThat(dayNight).doesNotContainKey("table");
        assertThat(lists.stream().noneMatch(row -> String.valueOf(row.get("key")).startsWith("enum_"))).isTrue();

        ResponseEntity<List> values = callList(token, "/api/v1/enums/day-night");
        assertThat(values.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map day = ((List<Map>) values.getBody()).stream().filter(row -> "DAY".equals(row.get("code"))).findFirst().orElseThrow();
        assertThat(day.get("label")).isEqualTo("Day");
        assertThat(day.get("active")).isEqualTo(true);

        ResponseEntity<Object> updated = call(token, HttpMethod.PUT, "/api/v1/enums/day-night/DAY",
                Map.of("code", "DAY", "label", "Daytime", "sortOrder", 0, "active", true));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        enums.seedMissing();
        ResponseEntity<Object> kept = call(token, HttpMethod.GET, "/api/v1/enums/day-night/DAY", null);
        assertThat(((Map) kept.getBody()).get("label")).isEqualTo("Daytime");

        ResponseEntity<Object> immutable = call(token, HttpMethod.PUT, "/api/v1/enums/day-night/DAY",
                Map.of("code", "NIGHT", "label", "Daytime", "sortOrder", 0, "active", true));
        assertThat(immutable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map) immutable.getBody()).get("code")).isEqualTo("CODE_IMMUTABLE");

        call(token, HttpMethod.PUT, "/api/v1/enums/day-night/DAY",
                Map.of("code", "DAY", "label", "Day", "sortOrder", 0, "active", false));
        ResponseEntity<List> choices = callList(token, "/api/v1/enums/day-night/choices");
        assertThat(choices.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((List<Map>) choices.getBody()).stream().noneMatch(row -> "DAY".equals(row.get("code")))).isTrue();
        ResponseEntity<List> management = callList(token, "/api/v1/enums/day-night");
        Map hidden = ((List<Map>) management.getBody()).stream().filter(row -> "DAY".equals(row.get("code"))).findFirst().orElseThrow();
        assertThat(hidden.get("active")).isEqualTo(false);
        call(token, HttpMethod.PUT, "/api/v1/enums/day-night/DAY",
                Map.of("code", "DAY", "label", "Day", "sortOrder", 0, "active", true));
    }

    @Test
    void rejectsBadWritesAndHasNoDelete() {
        String token = token("APEX", "123");
        ResponseEntity<Object> badCode = call(token, HttpMethod.POST, "/api/v1/enums/day-night",
                Map.of("code", "1BAD", "label", "Nope", "sortOrder", 9, "active", true));
        assertThat(badCode.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((Map) ((Map) badCode.getBody()).get("fieldErrors")).get("code")).isNotNull();

        ResponseEntity<Object> duplicate = call(token, HttpMethod.POST, "/api/v1/enums/day-night",
                Map.of("code", "DAYTIME", "label", "day", "sortOrder", 4, "active", true));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Object> withSide = call(token, HttpMethod.POST, "/api/v1/enums/day-night",
                Map.of("code", "EXTRA", "label", "Extra", "sortOrder", 4, "active", true, "sideId", 1));
        assertThat(withSide.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Object> choicesNeedSide = call(token, HttpMethod.GET, "/api/v1/enums/land-unit/choices", null);
        assertThat(choicesNeedSide.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Object> choicesRejectSide = call(token, HttpMethod.GET, "/api/v1/enums/day-night/choices?sideId=1", null);
        assertThat(choicesRejectSide.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Object> removed = call(token, HttpMethod.DELETE, "/api/v1/enums/day-night/DAY", null);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<Object> anonymous = rest.getForEntity("/api/v1/enums", Object.class);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void plannerCanReadChoicesButNotManage() {
        String admin = token("APEX", "123");
        ResponseEntity<List> roles = callList(admin, "/api/v1/roles");
        Map planner = ((List<Map>) roles.getBody()).stream().filter(row -> "Planner".equals(row.get("name"))).findFirst().orElseThrow();
        ResponseEntity<List> themes = callList(admin, "/api/v1/themes");
        Map free = ((List<Map>) themes.getBody()).stream().filter(row -> "FREE".equals(row.get("holderKind"))).findFirst().orElseThrow();
        String name = "planner-" + UUID.randomUUID().toString().substring(0, 8);
        ResponseEntity<Object> created = call(admin, HttpMethod.POST, "/api/v1/users",
                Map.of("name", name, "password", "secret", "roleIds", List.of(((Number) planner.get("id")).longValue()),
                        "themeId", ((Number) free.get("id")).longValue()));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String token = token(name, "secret");
        assertThat(call(token, HttpMethod.GET, "/api/v1/enums", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(token, HttpMethod.GET, "/api/v1/enums/day-night/choices", null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void sideRowsWaitForOneCountrySideAndBlockDelete() {
        String token = token("APEX", "123");
        Side first = country("Blue");
        Side second = country("Blue");
        enums.seedMissing();
        ResponseEntity<List> skipped = callList(token, "/api/v1/enums/land-unit");
        assertThat(((List<Map>) skipped.getBody()).stream().noneMatch(row -> "CORPS".equals(row.get("code")))).isTrue();

        sides.delete(second);
        enums.seedMissing();
        ResponseEntity<List> seeded = callList(token, "/api/v1/enums/land-unit");
        Map corps = ((List<Map>) seeded.getBody()).stream().filter(row -> "CORPS".equals(row.get("code"))).findFirst().orElseThrow();
        assertThat(corps.get("label")).isEqualTo("Corps");
        assertThat(((Number) corps.get("sideId")).longValue()).isEqualTo(first.getId());
        assertThat(corps.get("sideName")).isEqualTo("Blue");

        ResponseEntity<Object> blocked = call(token, HttpMethod.DELETE, "/api/v1/sides/" + first.getId(), null);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(((Map) blocked.getBody()).get("message").toString()).contains("enum");

        Side green = country("Green");
        ResponseEntity<Object> removed = call(token, HttpMethod.DELETE, "/api/v1/sides/" + green.getId(), null);
        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private Side country(String name) {
        Side side = new Side();
        side.setName(name);
        side.setKind(SideService.KIND_COUNTRY);
        return sides.save(side);
    }

    private String token(String name, String password) {
        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                Map.of("name", name, "password", password), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("token");
    }

    private ResponseEntity<Object> call(String token, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Object.class);
    }

    private ResponseEntity<List> callList(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), List.class);
    }
}
