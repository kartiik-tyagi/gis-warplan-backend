package com.drdo.jayant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import com.drdo.jayant.domain.AppUser;
import com.drdo.jayant.domain.RoleCodes;
import com.drdo.jayant.repository.AppUserRepository;
import com.drdo.jayant.repository.RoleRepository;

/** Own in-memory database so created masters do not disturb the seed assertions of other tests. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:mastersflow;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@SuppressWarnings({"unchecked", "rawtypes"})
class MastersAndOrbatFlowTest {

    @Autowired
    private TestRestTemplate rest;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private RoleRepository roles;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void apexLoginStillWorksAndReportsCanWork() {
        ResponseEntity<Map> ok = login("APEX", "123");
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map me = (Map) ok.getBody().get("me");
        assertThat(me.get("canWork")).isEqualTo(true);
        assertThat(login("APEX", "bad").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void sideStandardOrbatMarkUserScenarioGameAndAllianceFlow() {
        String admin = token("APEX", "123");
        String suffix = UUID.randomUUID().toString().substring(0, 6);

        Map alpha = created(call(admin, HttpMethod.POST, "/api/v1/sides",
                Map.of("name", "Alpha " + suffix, "colour", "#112233")));
        Map bravo = created(call(admin, HttpMethod.POST, "/api/v1/sides", Map.of("name", "Bravo " + suffix)));
        Long alphaId = id(alpha);
        Long bravoId = id(bravo);
        assertThat(alpha.get("kind")).isEqualTo("country");
        assertThat(call(admin, HttpMethod.POST, "/api/v1/sides", Map.of("name", "Alpha " + suffix)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Map nodeType = created(call(admin, HttpMethod.POST, "/api/v1/node-types",
                Map.of("code", "BRIGADE" + suffix.toUpperCase(), "name", "Brigade", "iconPath", "circle.svg")));

        // standard ORBAT: create root, then save a two-level tree
        ResponseEntity<Object> noOrbat = call(admin, HttpMethod.GET, "/api/v1/sides/" + alphaId + "/standard-orbat", null);
        assertThat(noOrbat.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<Object> createdOrbat = call(admin, HttpMethod.POST,
                "/api/v1/sides/" + alphaId + "/standard-orbat", null);
        assertThat(createdOrbat.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map orbat = (Map) createdOrbat.getBody();
        assertThat(call(admin, HttpMethod.POST, "/api/v1/sides/" + alphaId + "/standard-orbat", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        List<Map> nodes = (List<Map>) ((Map) orbat.get("orbatJson")).get("nodes");
        assertThat(nodes).hasSize(1);
        String rootId = (String) nodes.get(0).get("id");
        assertThat(nodes.get(0).get("parentId")).isNull();
        assertThat(nodes.get(0).get("name")).isEqualTo("Alpha " + suffix);
        assertThat(((Number) nodes.get(0).get("homeSideId")).longValue()).isEqualTo(alphaId);

        String childId = UUID.randomUUID().toString();
        List<Map> edited = new ArrayList<>(nodes);
        edited.add(node(childId, rootId, id(nodeType), alphaId, "1 Brigade"));
        Long version = ((Number) orbat.get("version")).longValue();
        ResponseEntity<Object> saved = call(admin, HttpMethod.PUT, "/api/v1/sides/" + alphaId + "/standard-orbat",
                Map.of("orbatJson", Map.of("nodes", edited), "version", version));
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) ((Map) saved.getBody()).get("version")).longValue()).isGreaterThan(version);

        // stale version, broken structure
        assertThat(call(admin, HttpMethod.PUT, "/api/v1/sides/" + alphaId + "/standard-orbat",
                Map.of("orbatJson", Map.of("nodes", edited), "version", version)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        Long current = ((Number) ((Map) saved.getBody()).get("version")).longValue();
        List<Map> orphan = new ArrayList<>(edited);
        orphan.add(node(UUID.randomUUID().toString(), "missing-parent", null, alphaId, "Orphan"));
        assertThat(call(admin, HttpMethod.PUT, "/api/v1/sides/" + alphaId + "/standard-orbat",
                Map.of("orbatJson", Map.of("nodes", orphan), "version", current)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(admin, HttpMethod.PUT, "/api/v1/sides/" + alphaId + "/standard-orbat",
                Map.of("orbatJson", Map.of("units", List.of()), "version", current)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        Long orbatId = id(orbat);
        Long commanderRole = roles.findByCode(RoleCodes.COMMANDER).orElseThrow().getId();

        // mark the child node as a user
        ResponseEntity<Object> marked = call(admin, HttpMethod.POST, "/api/v1/users/mark-from-node",
                Map.of("standardOrbatId", orbatId, "standardNodeId", childId, "name", "cmd" + suffix,
                        "password", "secret1", "roleIds", List.of(commanderRole)));
        assertThat(marked.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map markedUser = (Map) marked.getBody();
        assertThat(markedUser).doesNotContainKey("passwordHash");
        assertThat(markedUser.get("standardNodeId")).isEqualTo(childId);
        AppUser stored = users.findByName("cmd" + suffix).orElseThrow();
        assertThat(stored.getStandardOrbatId()).isEqualTo(orbatId);
        assertThat(passwordEncoder.matches("secret1", stored.getPasswordHash())).isTrue();
        assertThat(call(admin, HttpMethod.POST, "/api/v1/users/mark-from-node",
                Map.of("standardOrbatId", orbatId, "standardNodeId", childId, "name", "other" + suffix,
                        "password", "secret1", "roleIds", List.of(commanderRole))).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        // before anything is active the marked user can sign in but cannot work
        Map before = (Map) login("cmd" + suffix, "secret1").getBody().get("me");
        assertThat(before.get("canWork")).isEqualTo(false);
        assertThat(before.get("workBlockedReason")).isEqualTo("NO_ACTIVE_SCENARIO");

        // scenario, games, replica, finalize
        Map scenario = created(call(admin, HttpMethod.POST, "/api/v1/scenarios", Map.of("name", "Scn " + suffix)));
        Long scenarioId = id(scenario);
        Map game = created(call(admin, HttpMethod.POST, "/api/v1/scenarios/" + scenarioId + "/games",
                Map.of("name", "Game " + suffix, "moonphases", "Full moon on night 3")));
        assertThat(game.get("moonphases")).isEqualTo("Full moon on night 3");
        Map otherGame = created(call(admin, HttpMethod.POST, "/api/v1/scenarios/" + scenarioId + "/games",
                Map.of("name", "Game B " + suffix)));
        Long gameId = id(game);
        String orbats = "/api/v1/games/" + gameId + "/playing-orbats";
        assertThat(call(admin, HttpMethod.POST, "/api/v1/games/999999/playing-orbats/replica",
                Map.of("sideId", alphaId)).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        ResponseEntity<Object> replica = call(admin, HttpMethod.POST, orbats + "/replica", Map.of("sideId", alphaId));
        assertThat(replica.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map playing = (Map) replica.getBody();
        assertThat(((Number) playing.get("gameId")).longValue()).isEqualTo(gameId);
        assertThat(((Number) playing.get("scenarioId")).longValue()).isEqualTo(scenarioId);
        List<Map> playingNodes = (List<Map>) ((Map) playing.get("orbatJson")).get("nodes");
        assertThat(playingNodes).hasSize(2);
        assertThat(playingNodes).allSatisfy(n -> assertThat(n.get("sourceNodeId")).isNotNull());
        assertThat(playingNodes).noneMatch(n -> n.get("id").equals(rootId) || n.get("id").equals(childId));
        assertThat(((Map) playing.get("metadata")).get("status")).isEqualTo("draft");
        assertThat(call(admin, HttpMethod.POST, orbats + "/replica", Map.of("sideId", alphaId)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        // the same side may have its own playing ORBAT in another game of the scenario
        assertThat(call(admin, HttpMethod.POST, "/api/v1/games/" + id(otherGame) + "/playing-orbats/replica",
                Map.of("sideId", alphaId)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((List) call(admin, HttpMethod.GET, orbats, null).getBody())).hasSize(1);
        // an ORBAT is only reachable through the game that owns it
        assertThat(call(admin, HttpMethod.GET, "/api/v1/games/" + id(otherGame) + "/playing-orbats/" + id(playing),
                null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Long playingId = id(playing);
        Long playingVersion = ((Number) playing.get("version")).longValue();
        assertThat(call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("metadata", Map.of("status", "bogus"), "version", playingVersion)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        ResponseEntity<Object> finalized = call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("metadata", Map.of("status", "finalized"), "version", playingVersion));
        assertThat(finalized.getStatusCode()).isEqualTo(HttpStatus.OK);
        AppUser assigned = users.findByName("cmd" + suffix).orElseThrow();
        assertThat(assigned.getPlayingOrbatId()).isEqualTo(playingId);
        String playingChild = (String) playingNodes.stream()
                .filter(n -> childId.equals(n.get("sourceNodeId"))).findFirst().orElseThrow().get("id");
        assertThat(assigned.getPlayingNodeId()).isEqualTo(playingChild);

        // activation: scenario active, no active game -> blocked; another game active -> not assigned there;
        // the game owning the finalized playing ORBAT active -> works
        assertThat(call(admin, HttpMethod.POST, "/api/v1/scenarios/" + scenarioId + "/activate", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        Map noGame = (Map) login("cmd" + suffix, "secret1").getBody().get("me");
        assertThat(noGame.get("workBlockedReason")).isEqualTo("NO_ACTIVE_GAME");

        assertThat(call(admin, HttpMethod.POST, "/api/v1/games/" + id(otherGame) + "/activate", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        Map wrongGame = (Map) login("cmd" + suffix, "secret1").getBody().get("me");
        assertThat(wrongGame.get("canWork")).isEqualTo(false);
        assertThat(wrongGame.get("workBlockedReason")).isEqualTo("NOT_ASSIGNED");
        ResponseEntity<Object> activated = call(admin, HttpMethod.POST, "/api/v1/games/" + id(game) + "/activate", null);
        assertThat(((Map) activated.getBody()).get("active")).isEqualTo(true);
        List<Map> games = (List<Map>) call(admin, HttpMethod.GET, "/api/v1/scenarios/" + scenarioId + "/games", null)
                .getBody();
        assertThat(games.stream().filter(g -> Boolean.TRUE.equals(g.get("active")))).hasSize(1);
        assertThat(call(admin, HttpMethod.DELETE, "/api/v1/games/" + id(game), null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Map works = (Map) login("cmd" + suffix, "secret1").getBody().get("me");
        assertThat(works.get("canWork")).isEqualTo(true);
        assertThat(works.get("workBlockedReason")).isNull();
        assertThat(((Number) works.get("playingOrbatId")).longValue()).isEqualTo(playingId);

        // second scenario takes over as the only active one
        Map second = created(call(admin, HttpMethod.POST, "/api/v1/scenarios", Map.of("name", "Scn2 " + suffix)));
        call(admin, HttpMethod.POST, "/api/v1/scenarios/" + id(second) + "/activate", null);
        List<Map> allScenarios = (List<Map>) call(admin, HttpMethod.GET, "/api/v1/scenarios", null).getBody();
        assertThat(allScenarios.stream().filter(s -> Boolean.TRUE.equals(s.get("active")))).hasSize(1);
        assertThat(((Map) login("cmd" + suffix, "secret1").getBody().get("me")).get("canWork")).isEqualTo(false);

        // alliance of Alpha and Bravo (Bravo gets a standard ORBAT first)
        call(admin, HttpMethod.POST, "/api/v1/sides/" + bravoId + "/standard-orbat", null);
        ResponseEntity<Object> alliance = call(admin, HttpMethod.POST,
                "/api/v1/games/" + gameId + "/alliances",
                Map.of("name", "Pact " + suffix, "colour", "#aabbcc", "memberSideIds", List.of(alphaId, bravoId)));
        assertThat(alliance.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map allianceBody = (Map) alliance.getBody();
        assertThat(((Map) allianceBody.get("side")).get("kind")).isEqualTo("alliance");
        assertThat(((Number) ((Map) allianceBody.get("side")).get("gameId")).longValue()).isEqualTo(gameId);
        assertThat(((Number) ((Map) allianceBody.get("side")).get("scenarioId")).longValue()).isEqualTo(scenarioId);
        assertThat((List<Number>) allianceBody.get("memberSideIds")).hasSize(2);
        Long alliancePlayingId = ((Number) allianceBody.get("playingOrbatId")).longValue();
        Map alliancePlaying = (Map) call(admin, HttpMethod.GET, orbats + "/" + alliancePlayingId, null)
                .getBody();
        List<Map> allianceNodes = (List<Map>) ((Map) alliancePlaying.get("orbatJson")).get("nodes");
        // alliance root + alpha (2 nodes) + bravo (1 node)
        assertThat(allianceNodes).hasSize(4);
        assertThat(allianceNodes.stream().map(n -> ((Number) n.get("homeSideId")).longValue()).toList())
                .contains(alphaId, bravoId);

        assertThat(((List) call(admin, HttpMethod.GET, "/api/v1/games/" + gameId + "/alliances", null).getBody()))
                .hasSize(1);
        // country sides carry neither game nor scenario
        Map alphaAgain = (Map) call(admin, HttpMethod.GET, "/api/v1/sides/" + alphaId, null).getBody();
        assertThat(alphaAgain.get("gameId")).isNull();
        assertThat(alphaAgain.get("scenarioId")).isNull();

        // audit trail records the mutations
        Map auditPage = (Map) call(admin, HttpMethod.GET, "/api/v1/audit/logs?entityType=Scenario&size=100", null)
                .getBody();
        assertThat(((List<Map>) auditPage.get("items")).stream().map(i -> i.get("action")).toList())
                .contains("CREATE", "ACTIVATE");
        Map sessionPage = (Map) call(admin, HttpMethod.GET, "/api/v1/audit/sessions", null).getBody();
        assertThat((List<Map>) sessionPage.get("items")).isNotEmpty();
        assertThat(sessionPage.toString()).doesNotContain("tokenId");
    }

    @Test
    void functionalitiesAreEnforced() {
        String viewer = viewerToken("viewer1", RoleCodes.COMMANDER);
        assertThat(call(viewer, HttpMethod.GET, "/api/v1/functionalities", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.GET, "/api/v1/roles", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.POST, "/api/v1/themes", Map.of("name", "Nope")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.POST, "/api/v1/node-types", Map.of("code", "X", "name", "X")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.POST, "/api/v1/sides", Map.of("name", "Nope")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.GET, "/api/v1/users", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.GET, "/api/v1/audit/logs", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.POST, "/api/v1/scenarios", Map.of("name", "Nope")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(viewer, HttpMethod.PUT, "/api/v1/sides/1/standard-orbat",
                Map.of("orbatJson", Map.of("nodes", List.of()), "version", 0)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        // themes may be read by anyone signed in, and functionalities are read-only for everyone
        assertThat(call(viewer, HttpMethod.GET, "/api/v1/themes", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        String admin = token("APEX", "123");
        assertThat(call(admin, HttpMethod.GET, "/api/v1/functionalities", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(admin, HttpMethod.POST, "/api/v1/functionalities", Map.of("code", "X")).getStatusCode().value())
                .isIn(404, 405);
        assertThat(call(null, HttpMethod.GET, "/api/v1/functionalities", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void sideAdministratorIsScopedAndCannotEscalate() {
        String admin = token("APEX", "123");
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        Long own = id(created(call(admin, HttpMethod.POST, "/api/v1/sides", Map.of("name", "Own " + suffix))));
        Long foreign = id(created(call(admin, HttpMethod.POST, "/api/v1/sides", Map.of("name", "Foreign " + suffix))));

        AppUser sideAdmin = new AppUser();
        sideAdmin.setName("tadmin" + suffix);
        sideAdmin.setPasswordHash(passwordEncoder.encode("pw1234"));
        sideAdmin.setRoleIds(List.of(roles.findByCode(RoleCodes.SIDE_ADMINISTRATOR).orElseThrow().getId()));
        sideAdmin.setAdministeredSideId(own);
        users.save(sideAdmin);
        String token = token("tadmin" + suffix, "pw1234");

        assertThat(call(token, HttpMethod.PUT, "/api/v1/sides/" + own, Map.of("name", "Own renamed " + suffix))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(token, HttpMethod.PUT, "/api/v1/sides/" + foreign, Map.of("name", "Hijack " + suffix))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(token, HttpMethod.POST, "/api/v1/sides", Map.of("name", "New " + suffix)).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(token, HttpMethod.POST, "/api/v1/sides/" + foreign + "/standard-orbat", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(token, HttpMethod.POST, "/api/v1/sides/" + own + "/standard-orbat", null).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        List<Map> visible = (List<Map>) call(token, HttpMethod.GET, "/api/v1/users", null).getBody();
        assertThat(visible).extracting(u -> u.get("name")).containsExactly("tadmin" + suffix);

        Long adminRole = roles.findByCode(RoleCodes.ADMINISTRATOR).orElseThrow().getId();
        assertThat(call(token, HttpMethod.PATCH, "/api/v1/users/" + sideAdmin.getId(),
                Map.of("roleIds", List.of(adminRole))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(token, HttpMethod.PATCH, "/api/v1/users/" + users.findByName("APEX").orElseThrow().getId(),
                Map.of("active", false)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void chooseThemeAndPatchUser() {
        String admin = token("APEX", "123");
        Long themeId = id(created(call(admin, HttpMethod.POST, "/api/v1/themes",
                Map.of("name", "Night " + UUID.randomUUID(), "configJson", Map.of("mode", "dark")))));
        ResponseEntity<Object> chosen = call(admin, HttpMethod.PATCH, "/api/v1/users/me/theme", Map.of("themeId", themeId));
        assertThat(chosen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) ((Map) chosen.getBody()).get("themeId")).longValue()).isEqualTo(themeId);
        assertThat(call(admin, HttpMethod.PATCH, "/api/v1/users/me/theme", Map.of("themeId", 987654)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(admin, HttpMethod.DELETE, "/api/v1/themes/" + themeId, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Long apexId = users.findByName("APEX").orElseThrow().getId();
        assertThat(call(admin, HttpMethod.PATCH, "/api/v1/users/" + apexId, Map.of("active", false)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(admin, HttpMethod.GET, "/api/v1/users/" + apexId, null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(admin, HttpMethod.GET, "/api/v1/users/abc", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(call(admin, HttpMethod.GET, "/api/v1/users/999999", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- helpers

    private String viewerToken(String name, String roleCode) {
        if (users.findByName(name).isEmpty()) {
            AppUser user = new AppUser();
            user.setName(name);
            user.setPasswordHash(passwordEncoder.encode("pw1234"));
            user.setRoleIds(List.of(roles.findByCode(roleCode).orElseThrow().getId()));
            users.save(user);
        }
        return token(name, "pw1234");
    }

    private static Map<String, Object> node(String id, String parentId, Long nodeTypeId, Long homeSideId, String name) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("parentId", parentId);
        node.put("nodeTypeId", nodeTypeId);
        node.put("homeSideId", homeSideId);
        node.put("name", name);
        return node;
    }

    private static Long id(Map body) {
        return ((Number) body.get("id")).longValue();
    }

    private static Map created(ResponseEntity<Object> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (Map) response.getBody();
    }

    private ResponseEntity<Map> login(String name, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("name", name, "password", password), headers), Map.class);
    }

    private String token(String name, String password) {
        ResponseEntity<Map> response = login(name, password);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("token");
    }

    private ResponseEntity<Object> call(String token, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (body != null) {
            headers.setContentType(MediaType.APPLICATION_JSON);
        }
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Object.class);
    }
}
