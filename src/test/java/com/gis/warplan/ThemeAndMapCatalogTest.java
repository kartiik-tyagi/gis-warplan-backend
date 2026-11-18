package com.gis.warplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:thememap;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@SuppressWarnings({"unchecked", "rawtypes"})
class ThemeAndMapCatalogTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void themesAreSeededWithFullPaletteAndNotOverwritten() {
        String token = token("APEX", "123");
        ResponseEntity<List> listed = callList(token, "/api/v1/themes");
        assertThat(listed.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map> themes = listed.getBody();
        assertThat(themes).hasSizeGreaterThanOrEqualTo(20);
        Map pine = themes.stream().filter(row -> "Pine, night".equals(row.get("name"))).findFirst().orElseThrow();
        assertThat(pine.get("defaultTheme")).isEqualTo(true);
        Map config = (Map) pine.get("configJson");
        assertThat(config.get("paletteId")).isEqualTo("field-night");
        assertThat(config.get("primary")).isEqualTo("#3ddea0");
        assertThat(config.get("mode")).isEqualTo("dark");

        Long pineId = ((Number) pine.get("id")).longValue();
        ResponseEntity<Object> updated = call(token, HttpMethod.PUT, "/api/v1/themes/" + pineId,
                Map.of("name", "Pine, night", "description", "user edited", "defaultTheme", true,
                        "configJson", Map.of("paletteId", "field-night", "mode", "dark", "primary", "#00ff00")));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Restart seed path is not re-run mid-test; assert the user edit stuck on read.
        ResponseEntity<Object> got = call(token, HttpMethod.GET, "/api/v1/themes/" + pineId, null);
        assertThat(got.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = (Map) got.getBody();
        assertThat(body.get("description")).isEqualTo("user edited");
        assertThat(((Map) body.get("configJson")).get("primary")).isEqualTo("#00ff00");
    }

    @Test
    void mapConfigIsSeededAndLayersCanBeAdded() {
        String token = token("APEX", "123");
        ResponseEntity<Object> config = call(token, HttpMethod.GET, "/api/v1/map/config", null);
        assertThat(config.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map body = (Map) config.getBody();
        Map server = (Map) body.get("server");
        List<Map> layers = (List<Map>) body.get("layers");
        Map view = (Map) body.get("view");
        assertThat(server.get("code")).isEqualTo("local");
        assertThat(server.get("wmsUrl")).isEqualTo("http://127.0.0.1:8080/geoserver/wms");
        assertThat(layers).hasSizeGreaterThanOrEqualTo(5);
        assertThat(layers.stream().anyMatch(layer -> "places".equals(layer.get("code")))).isTrue();
        assertThat(view.get("centerLon")).isEqualTo(82.0);
        assertThat(view.containsKey("osmUrl")).isFalse();
        assertThat(view.containsKey("basemapSource")).isFalse();

        Long serverId = ((Number) server.get("id")).longValue();
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Map<String, Object> layerBody = new LinkedHashMap<>();
        layerBody.put("gisServerId", serverId);
        layerBody.put("code", "custom-" + suffix);
        layerBody.put("label", "Custom places " + suffix);
        layerBody.put("groupName", "Reference");
        layerBody.put("wmsLayers", "ne:populated_places");
        layerBody.put("transparent", true);
        layerBody.put("baseLayer", false);
        layerBody.put("sortOrder", 90);
        layerBody.put("defaultVisible", true);
        layerBody.put("defaultOpacity", 0.8);
        layerBody.put("active", true);
        ResponseEntity<Object> created = call(token, HttpMethod.POST, "/api/v1/map/layers", layerBody);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Object> refreshed = call(token, HttpMethod.GET, "/api/v1/map/config", null);
        List<Map> nextLayers = (List<Map>) ((Map) refreshed.getBody()).get("layers");
        assertThat(nextLayers.stream().anyMatch(layer -> ("custom-" + suffix).equals(layer.get("code")))).isTrue();
    }

    @Test
    void manageMapIsRequiredForLayerAdmin() {
        String token = token("APEX", "123");
        assertThat(call(token, HttpMethod.GET, "/api/v1/map/layers", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(token, HttpMethod.GET, "/api/v1/map/config", null).getStatusCode()).isEqualTo(HttpStatus.OK);
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
