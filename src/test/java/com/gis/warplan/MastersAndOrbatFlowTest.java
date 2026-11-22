package com.gis.warplan;

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

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.RoleCodes;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.RoleRepository;

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

        Map alpha = created(call(admin, HttpMethod.POST, "/api/v1/sides", force(admin, "Alpha " + suffix)));
        Map bravo = created(call(admin, HttpMethod.POST, "/api/v1/sides", force(admin, "Bravo " + suffix)));
        Long alphaId = id(alpha);
        Long bravoId = id(bravo);
        assertThat(alpha.get("kind")).isEqualTo("country");
        assertThat(alpha.get("themeId")).isNotNull();
        assertThat(call(admin, HttpMethod.POST, "/api/v1/sides",
                Map.of("name", "Other " + suffix, "themeId", alpha.get("themeId"))).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(admin, HttpMethod.POST, "/api/v1/sides", Map.of("name", "Alpha " + suffix)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Map nodeType = created(call(admin, HttpMethod.POST, "/api/v1/node-types",
                Map.of("code", "BRIGADE" + suffix.toUpperCase(), "name", "Brigade", "iconPath", "circle.svg")));

        // the force and its root are created together; a second create returns the same tree
        ResponseEntity<Object> createdOrbat = call(admin, HttpMethod.GET, "/api/v1/sides/" + alphaId + "/standard-orbat", null);
        assertThat(createdOrbat.getStatusCode()).isEqualTo(HttpStatus.OK);
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
        assertThat(markedUser.get("active")).isEqualTo(false);
        assertThat(((Number) markedUser.get("administeredSideId")).longValue()).isEqualTo(alphaId);
        AppUser stored = users.findByName("cmd" + suffix).orElseThrow();
        assertThat(stored.getStandardOrbatId()).isEqualTo(orbatId);
        assertThat(stored.getAdministeredSideId()).isEqualTo(alphaId);
        assertThat(passwordEncoder.matches("secret1", stored.getPasswordHash())).isTrue();
        assertThat(call(admin, HttpMethod.PATCH, "/api/v1/users/" + stored.getId(),
                Map.of("administeredSideId", bravoId)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(users.findByName("cmd" + suffix).orElseThrow().getAdministeredSideId()).isEqualTo(alphaId);
        assertThat(call(admin, HttpMethod.POST, "/api/v1/users/mark-from-node",
                Map.of("standardOrbatId", orbatId, "standardNodeId", childId, "name", "other" + suffix,
                        "password", "secret1", "roleIds", List.of(commanderRole))).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<Object> renamed = call(admin, HttpMethod.PATCH, "/api/v1/users/marked",
                Map.of("standardOrbatId", orbatId, "standardNodeId", childId, "name", "cmdx" + suffix,
                        "roleIds", List.of(commanderRole)));
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) renamed.getBody()).get("name")).isEqualTo("cmdx" + suffix);
        assertThat(call(admin, HttpMethod.PATCH, "/api/v1/users/marked",
                Map.of("standardOrbatId", orbatId, "standardNodeId", childId, "name", "cmd" + suffix,
                        "roleIds", List.of(commanderRole))).getStatusCode()).isEqualTo(HttpStatus.OK);
        Map markedAgain = (Map) call(admin, HttpMethod.GET,
                "/api/v1/users/marked?standardOrbatId=" + orbatId + "&standardNodeId=" + childId, null).getBody();
        assertThat(markedAgain.get("name")).isEqualTo("cmd" + suffix);

        // a marked user stays off the login list until their playing ORBAT's game is active
        assertThat(login("cmd" + suffix, "secret1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map loginContext = (Map) call(null, HttpMethod.GET, "/api/v1/auth/login-context", null).getBody();
        assertThat(((List<Map>) loginContext.get("activeUsers")).stream().map(user -> user.get("name")).toList())
                .doesNotContain("cmd" + suffix);
        assertThat(login("APEX", "123").getStatusCode()).isEqualTo(HttpStatus.OK);
        Map fetched = (Map) call(admin, HttpMethod.GET, "/api/v1/users/" + stored.getId(), null).getBody();
        assertThat(((Number) fetched.get("displayThemeId")).longValue())
                .isEqualTo(((Number) alpha.get("themeId")).longValue());

        // scenario, games, replica, finalize
        Map scenario = created(call(admin, HttpMethod.POST, "/api/v1/scenarios", Map.of("name", "Scn " + suffix)));
        Long scenarioId = id(scenario);
        Map game = created(call(admin, HttpMethod.POST, "/api/v1/scenarios/" + scenarioId + "/games",
                Map.of("name", "Game " + suffix, "moonphases", "FULL_MOON")));
        assertThat(game.get("moonphases")).isEqualTo("FULL_MOON");
        Map otherGame = created(call(admin, HttpMethod.POST, "/api/v1/scenarios/" + scenarioId + "/games",
                Map.of("name", "Game B " + suffix)));
        Long gameId = id(game);
        String orbats = "/api/v1/games/" + gameId + "/playing-orbats";
        ResponseEntity<Object> noOrbat = call(admin, HttpMethod.POST, "/api/v1/games/" + gameId + "/activate", null);
        assertThat(noOrbat.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(((Map) noOrbat.getBody()).get("message"))).contains("no playing ORBAT");
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
        ResponseEntity<Object> stillDraft = call(admin, HttpMethod.POST, "/api/v1/games/" + gameId + "/activate", null);
        assertThat(stillDraft.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(((Map) stillDraft.getBody()).get("message"))).contains("Finalize every playing ORBAT");
        ResponseEntity<Object> otherDraft = call(admin, HttpMethod.POST, "/api/v1/games/" + id(otherGame) + "/activate", null);
        assertThat(otherDraft.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
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
        String playingChild = (String) playingNodes.stream()
                .filter(n -> childId.equals(n.get("sourceNodeId"))).findFirst().orElseThrow().get("id");
        List<Map> heldNodes = new ArrayList<>();
        for (Map node : playingNodes) {
            Map<String, Object> copy = new LinkedHashMap<>(node);
            if (childId.equals(copy.get("sourceNodeId"))) {
                copy.put("participationHeld", true);
            }
            heldNodes.add(copy);
        }
        ResponseEntity<Object> heldSave = call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("orbatJson", Map.of("nodes", heldNodes), "metadata", Map.of("status", "draft"),
                        "version", playingVersion));
        assertThat(heldSave.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long heldVersion = ((Number) ((Map) heldSave.getBody()).get("version")).longValue();
        ResponseEntity<Object> heldFinal = call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("metadata", Map.of("status", "finalized"), "version", heldVersion));
        assertThat(heldFinal.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(users.findByName("cmd" + suffix).orElseThrow().isActive()).isFalse();
        assertThat(call(admin, HttpMethod.POST, "/api/v1/scenarios/" + scenarioId + "/activate", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(login("cmd" + suffix, "secret1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ResponseEntity<Object> heldActive = call(admin, HttpMethod.POST, "/api/v1/games/" + gameId + "/activate", null);
        assertThat(heldActive.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) heldActive.getBody()).get("active")).isEqualTo(true);
        assertThat(login("cmd" + suffix, "secret1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        Long heldFinalVersion = ((Number) ((Map) heldFinal.getBody()).get("version")).longValue();
        ResponseEntity<Object> reopenedHold = call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("metadata", Map.of("status", "draft"), "version", heldFinalVersion));
        assertThat(reopenedHold.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) call(admin, HttpMethod.GET, "/api/v1/games/" + gameId, null).getBody()).get("active"))
                .isEqualTo(true);
        assertThat(login("cmd" + suffix, "secret1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        List<Map> releasedNodes = new ArrayList<>();
        for (Map node : heldNodes) {
            Map<String, Object> copy = new LinkedHashMap<>(node);
            copy.put("participationHeld", false);
            releasedNodes.add(copy);
        }
        Long reopenHoldVersion = ((Number) ((Map) reopenedHold.getBody()).get("version")).longValue();
        ResponseEntity<Object> released = call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("orbatJson", Map.of("nodes", releasedNodes), "metadata", Map.of("status", "draft"),
                        "version", reopenHoldVersion));
        assertThat(released.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long releasedVersion = ((Number) ((Map) released.getBody()).get("version")).longValue();
        ResponseEntity<Object> finalized = call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("metadata", Map.of("status", "finalized"), "version", releasedVersion));
        assertThat(finalized.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map releasedLogin = (Map) login("cmd" + suffix, "secret1").getBody().get("me");
        assertThat(releasedLogin.get("canWork")).isEqualTo(true);

        ResponseEntity<Object> activated = call(admin, HttpMethod.POST, "/api/v1/games/" + gameId + "/activate", null);
        assertThat(((Map) activated.getBody()).get("active")).isEqualTo(true);
        List<Map> games = (List<Map>) call(admin, HttpMethod.GET, "/api/v1/scenarios/" + scenarioId + "/games", null)
                .getBody();
        assertThat(games.stream().filter(g -> Boolean.TRUE.equals(g.get("active")))).hasSize(1);
        assertThat(call(admin, HttpMethod.DELETE, "/api/v1/games/" + gameId, null).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Map works = (Map) login("cmd" + suffix, "secret1").getBody().get("me");
        assertThat(works.get("canWork")).isEqualTo(true);
        assertThat(works.get("workBlockedReason")).isNull();
        assertThat(((Number) works.get("playingOrbatId")).longValue()).isEqualTo(playingId);
        assertThat(works.get("playingNodeId")).isEqualTo(playingChild);
        assertSituation(token("cmd" + suffix, "secret1"), gameId, playingId);

        assertThat(call(admin, HttpMethod.POST, "/api/v1/games/deactivate", null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(login("cmd" + suffix, "secret1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(call(admin, HttpMethod.POST, "/api/v1/games/" + gameId + "/activate", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // a finalized ORBAT stays locked until it is explicitly re-edited, then must be finalized again
        Long lockedVersion = ((Number) ((Map) finalized.getBody()).get("version")).longValue();
        assertThat(call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("orbatJson", playing.get("orbatJson"), "version", lockedVersion)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<Object> reopened = call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("metadata", Map.of("status", "draft"), "version", lockedVersion));
        assertThat(reopened.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) ((Map) reopened.getBody()).get("metadata")).get("status")).isEqualTo("draft");
        assertThat(((Map) call(admin, HttpMethod.GET, "/api/v1/games/" + gameId, null).getBody()).get("active"))
                .isEqualTo(true);
        assertThat(login("cmd" + suffix, "secret1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Map hidden = (Map) call(admin, HttpMethod.GET, "/api/v1/situation", null).getBody();
        assertThat(((Number) hidden.get("gameId")).longValue()).isEqualTo(gameId);
        assertThat((List) hidden.get("orbats")).isEmpty();
        assertThat(call(null, HttpMethod.GET, "/api/v1/situation", null).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        Long draftVersion = ((Number) ((Map) reopened.getBody()).get("version")).longValue();
        ResponseEntity<Object> again = call(admin, HttpMethod.PUT, orbats + "/" + playingId,
                Map.of("metadata", Map.of("status", "finalized"), "version", draftVersion));
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map resumed = (Map) login("cmd" + suffix, "secret1").getBody().get("me");
        assertThat(resumed.get("canWork")).isEqualTo(true);
        assertThat(resumed.get("playingNodeId")).isEqualTo(playingChild);
        assertSituation(token("cmd" + suffix, "secret1"), gameId, playingId);
        Map apexPicture = (Map) call(admin, HttpMethod.GET, "/api/v1/situation", null).getBody();
        assertThat(idsOf(apexPicture)).contains(playingId);

        // second scenario takes over as the only active one
        Map second = created(call(admin, HttpMethod.POST, "/api/v1/scenarios", Map.of("name", "Scn2 " + suffix)));
        call(admin, HttpMethod.POST, "/api/v1/scenarios/" + id(second) + "/activate", null);
        List<Map> allScenarios = (List<Map>) call(admin, HttpMethod.GET, "/api/v1/scenarios", null).getBody();
        assertThat(allScenarios.stream().filter(s -> Boolean.TRUE.equals(s.get("active")))).hasSize(1);
        assertThat(login("cmd" + suffix, "secret1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // rich HTML narrative persists; deactivate clears every active scenario and game
        String boldIdea = "<p>Advance on <strong>Hill 42</strong></p>";
        ResponseEntity<Object> rich = call(admin, HttpMethod.PUT, "/api/v1/scenarios/" + scenarioId,
                Map.of("name", "Scn " + suffix, "generalIdea", boldIdea, "openingNarrative",
                        "<p>Opening with <em>emphasis</em></p>", "specialIdea", "<ul><li>One</li></ul>"));
        assertThat(rich.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map) rich.getBody()).get("generalIdea")).isEqualTo(boldIdea);
        assertThat(call(admin, HttpMethod.POST, "/api/v1/scenarios/deactivate", null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        List<Map> cleared = (List<Map>) call(admin, HttpMethod.GET, "/api/v1/scenarios", null).getBody();
        assertThat(cleared.stream().filter(s -> Boolean.TRUE.equals(s.get("active")))).isEmpty();
        List<Map> clearedGames = (List<Map>) call(admin, HttpMethod.GET, "/api/v1/scenarios/" + scenarioId + "/games",
                null).getBody();
        assertThat(clearedGames.stream().filter(g -> Boolean.TRUE.equals(g.get("active")))).isEmpty();
        assertThat(login("cmd" + suffix, "secret1").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login("APEX", "123").getStatusCode()).isEqualTo(HttpStatus.OK);

        // a side already playing alone cannot join an alliance, and an alliance member cannot also play alone
        Map charlie = created(call(admin, HttpMethod.POST, "/api/v1/sides", force(admin, "Charlie " + suffix)));
        Long charlieId = id(charlie);
        call(admin, HttpMethod.POST, "/api/v1/sides/" + bravoId + "/standard-orbat", null);
        Long allianceTheme = id(created(call(admin, HttpMethod.POST, "/api/v1/themes",
                Map.of("name", "Pact theme " + suffix, "configJson", Map.of("mode", "dark")))));
        assertThat(call(admin, HttpMethod.POST, "/api/v1/games/" + gameId + "/alliances",
                Map.of("name", "Blocked " + suffix, "colour", "#aabbcc", "themeId", allianceTheme,
                        "memberSideIds", List.of(alphaId, bravoId))).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        ResponseEntity<Object> alliance = call(admin, HttpMethod.POST,
                "/api/v1/games/" + gameId + "/alliances",
                Map.of("name", "Pact " + suffix, "colour", "#aabbcc", "themeId", allianceTheme,
                        "memberSideIds", List.of(bravoId, charlieId)));
        assertThat(alliance.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map allianceBody = (Map) alliance.getBody();
        assertThat(((Map) allianceBody.get("side")).get("kind")).isEqualTo("alliance");
        assertThat(((Number) ((Map) allianceBody.get("side")).get("gameId")).longValue()).isEqualTo(gameId);
        assertThat(((Number) ((Map) allianceBody.get("side")).get("scenarioId")).longValue()).isEqualTo(scenarioId);
        assertThat((List<Number>) allianceBody.get("memberSideIds")).hasSize(2);
        Long allianceSideId = id((Map) allianceBody.get("side"));
        Long alliancePlayingId = ((Number) allianceBody.get("playingOrbatId")).longValue();
        Map alliancePlaying = (Map) call(admin, HttpMethod.GET, orbats + "/" + alliancePlayingId, null)
                .getBody();
        List<Map> allianceNodes = (List<Map>) ((Map) alliancePlaying.get("orbatJson")).get("nodes");
        // alliance root + bravo root + charlie root
        assertThat(allianceNodes).hasSize(3);
        assertThat(allianceNodes.stream().map(n -> ((Number) n.get("homeSideId")).longValue()).toList())
                .contains(bravoId, charlieId);
        assertThat(call(admin, HttpMethod.POST, orbats + "/replica", Map.of("sideId", bravoId)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(call(admin, HttpMethod.POST, "/api/v1/games/" + id(otherGame) + "/playing-orbats/replica",
                Map.of("sideId", bravoId)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long secondTheme = id(created(call(admin, HttpMethod.POST, "/api/v1/themes",
                Map.of("name", "Second pact " + suffix, "configJson", Map.of("mode", "dark")))));
        assertThat(call(admin, HttpMethod.POST, "/api/v1/games/" + gameId + "/alliances",
                Map.of("name", "Other pact " + suffix, "colour", "#ccbbaa", "themeId", secondTheme,
                        "memberSideIds", List.of(bravoId, charlieId))).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Map bravoOrbat = (Map) call(admin, HttpMethod.GET, "/api/v1/sides/" + bravoId + "/standard-orbat", null).getBody();
        String bravoRoot = (String) ((List<Map>) ((Map) bravoOrbat.get("orbatJson")).get("nodes")).get(0).get("id");
        assertThat(call(admin, HttpMethod.POST, "/api/v1/users/mark-from-node",
                Map.of("standardOrbatId", id(bravoOrbat), "standardNodeId", bravoRoot, "name", "ally" + suffix,
                        "password", "secret1", "roleIds", List.of(commanderRole))).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        Long allianceVersion = ((Number) alliancePlaying.get("version")).longValue();
        assertThat(call(admin, HttpMethod.PUT, orbats + "/" + alliancePlayingId,
                Map.of("metadata", Map.of("status", "finalized"), "version", allianceVersion)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(users.findByName("ally" + suffix).orElseThrow().getPlayingOrbatId()).isNull();
        assertThat(call(admin, HttpMethod.POST, "/api/v1/games/" + gameId + "/activate", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(users.findByName("ally" + suffix).orElseThrow().getPlayingOrbatId()).isEqualTo(alliancePlayingId);
        Map allyPicture = (Map) call(token("ally" + suffix, "secret1"), HttpMethod.GET, "/api/v1/situation", null).getBody();
        assertThat(idsOf(allyPicture)).containsExactly(alliancePlayingId);
        Map allyOrbat = (Map) ((List) allyPicture.get("orbats")).get(0);
        assertThat(allyOrbat.get("alliance")).isEqualTo(true);
        assertThat(((List<Map>) allyOrbat.get("nodes")).stream().map(n -> ((Number) n.get("homeSideId")).longValue()).toList())
                .contains(bravoId, charlieId);
        assertSituation(token("cmd" + suffix, "secret1"), gameId, playingId);
        assertThat(idsOf((Map) call(admin, HttpMethod.GET, "/api/v1/situation", null).getBody()))
                .contains(playingId, alliancePlayingId);

        assertThat(((List) call(admin, HttpMethod.GET, "/api/v1/games/" + gameId + "/alliances", null).getBody()))
                .hasSize(1);
        assertThat(call(admin, HttpMethod.DELETE, orbats + "/" + alliancePlayingId, null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(((List) call(admin, HttpMethod.GET, "/api/v1/games/" + gameId + "/alliances", null).getBody()))
                .isEmpty();
        assertThat(call(admin, HttpMethod.GET, "/api/v1/sides/" + allianceSideId, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(admin, HttpMethod.POST, orbats + "/replica", Map.of("sideId", charlieId)).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
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
        assertThat(sessionPage.toString()).doesNotContain("sessionTokenId");
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
        Long own = id(created(call(admin, HttpMethod.POST, "/api/v1/sides", force(admin, "Own " + suffix))));
        Long foreign = id(created(call(admin, HttpMethod.POST, "/api/v1/sides", force(admin, "Foreign " + suffix))));

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
                .isEqualTo(HttpStatus.OK);

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

    @Test
    void nodeTypePropertiesRoundTripAndOrbatRules() {
        String admin = token("APEX", "123");
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        String parentCode = "WING" + suffix;
        String renamedCode = "WING2" + suffix;
        String childCode = "FLIGHT" + suffix;

        Map<String, Object> parent = created(call(admin, HttpMethod.POST, "/api/v1/node-types",
                nodeTypeBody(parentCode, "Wing", null)));
        @SuppressWarnings("unchecked")
        Map<String, Object> parentProperties = (Map<String, Object>) parent.get("properties");
        assertThat(parentProperties.get("seeded")).isEqualTo(false);
        assertThat(parentProperties.get("root")).isEqualTo(false);
        assertThat(parentProperties).doesNotContainKey("parents");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("seeded", false);
        properties.put("root", false);
        properties.put("parents", List.of(parentCode.toLowerCase()));
        properties.put("fields", List.of(
                choiceField("role", "Role", true, List.of("Air Defence", "assault")),
                choiceField("strength", "Strength", false, null)));
        ((Map<String, Object>) ((List<?>) properties.get("fields")).get(1)).put("type", "number");
        ((Map<String, Object>) ((List<?>) properties.get("fields")).get(1)).remove("options");
        Map<String, Object> child = created(call(admin, HttpMethod.POST, "/api/v1/node-types",
                nodeTypeBody(childCode, "Flight", properties)));
        @SuppressWarnings("unchecked")
        Map<String, Object> stored = (Map<String, Object>) child.get("properties");
        assertThat(stored.get("parents")).isEqualTo(List.of(parentCode));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> storedFields = (List<Map<String, Object>>) stored.get("fields");
        assertThat(storedFields.get(0).get("options")).isEqualTo(List.of("air_defence", "assault"));
        assertThat(storedFields.get(1)).doesNotContainKey("options");

        ResponseEntity<Object> seededAttempt = call(admin, HttpMethod.POST, "/api/v1/node-types",
                nodeTypeBody("BAD" + suffix, "Bad", Map.of("seeded", true)));
        assertThat(seededAttempt.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(fieldErrors(seededAttempt)).containsEntry("properties.seeded", "Seeded types come from the catalogue.");
        assertThat(call(admin, HttpMethod.POST, "/api/v1/node-types", nodeTypeBody(childCode, "Again", null)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> renamedBody = nodeTypeBody(renamedCode, "Wing", NodeTypeRulesEmpty());
        ResponseEntity<Object> renamedResponse = call(admin, HttpMethod.PUT, "/api/v1/node-types/" + id(parent), renamedBody);
        assertThat(renamedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> childAgain = (Map<String, Object>) call(admin, HttpMethod.GET, "/api/v1/node-types/" + id(child), null)
                .getBody();
        assertThat(((Map<?, ?>) childAgain.get("properties")).get("parents")).isEqualTo(List.of(renamedCode));

        ResponseEntity<Object> blockedDelete = call(admin, HttpMethod.DELETE, "/api/v1/node-types/" + id(parent), null);
        assertThat(blockedDelete.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(String.valueOf(((Map<?, ?>) blockedDelete.getBody()).get("message"))).contains("Flight");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> catalogue = (List<Map<String, Object>>) call(admin, HttpMethod.GET, "/api/v1/node-types", null)
                .getBody();
        Map<String, Object> theatre = catalogue.stream().filter(type -> "THEATRE".equals(type.get("code"))).findFirst().orElseThrow();
        Object theatreFields = ((Map<?, ?>) theatre.get("properties")).get("fields");
        Map<String, Object> hostile = nodeTypeBody("THEATRE_RENAMED", "Theatre", Map.of("seeded", false, "root", false, "fields", List.of()));
        hostile.put("description", theatre.get("description"));
        hostile.put("iconPath", theatre.get("iconPath"));
        ResponseEntity<Object> theatreUpdate = call(admin, HttpMethod.PUT, "/api/v1/node-types/" + id(theatre), hostile);
        assertThat(theatreUpdate.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> theatreAfter = (Map<String, Object>) theatreUpdate.getBody();
        assertThat(theatreAfter.get("code")).isEqualTo("THEATRE");
        assertThat(((Map<?, ?>) theatreAfter.get("properties")).get("fields")).isEqualTo(theatreFields);

        Map<String, Object> side = created(call(admin, HttpMethod.POST, "/api/v1/sides", force(admin, "Props " + suffix)));
        Long sideId = id(side);
        @SuppressWarnings("unchecked")
        Map<String, Object> orbat = (Map<String, Object>) call(admin, HttpMethod.GET, "/api/v1/sides/" + sideId + "/standard-orbat", null)
                .getBody();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = new ArrayList<>((List<Map<String, Object>>) ((Map<?, ?>) orbat.get("orbatJson")).get("nodes"));
        String rootId = (String) nodes.get(0).get("id");
        long version = ((Number) orbat.get("version")).longValue();
        String wingId = UUID.randomUUID().toString();
        String flightId = UUID.randomUUID().toString();
        nodes.add(node(wingId, rootId, id(parent), sideId, "1 Wing"));
        Map<String, Object> flight = node(flightId, wingId, id(child), sideId, "1 Flight");
        nodes.add(flight);

        ResponseEntity<Object> missing = saveOrbat(admin, sideId, nodes, version);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(((Map<?, ?>) missing.getBody()).get("message"))).contains("Role");

        flight.put("role", "nope");
        assertThat(saveOrbat(admin, sideId, nodes, version).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        flight.put("role", "assault");
        flight.put("strength", "lots");
        ResponseEntity<Object> badNumber = saveOrbat(admin, sideId, nodes, version);
        assertThat(badNumber.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(((Map<?, ?>) badNumber.getBody()).get("message"))).contains("Strength");
        flight.remove("strength");
        flight.put("parentId", rootId);
        ResponseEntity<Object> badParent = saveOrbat(admin, sideId, nodes, version);
        assertThat(badParent.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(((Map<?, ?>) badParent.getBody()).get("message"))).contains("cannot sit under");
        flight.put("parentId", wingId);

        Map<String, Object> rootOnly = created(call(admin, HttpMethod.POST, "/api/v1/node-types",
                nodeTypeBody("TOP" + suffix, "Top", Map.of("root", true, "fields", List.of()))));
        nodes.add(node(UUID.randomUUID().toString(), rootId, id(rootOnly), sideId, "Nested top"));
        ResponseEntity<Object> nestedRoot = saveOrbat(admin, sideId, nodes, version);
        assertThat(nestedRoot.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(((Map<?, ?>) nestedRoot.getBody()).get("message"))).contains("only sit at the root");
        nodes.remove(nodes.size() - 1);

        ResponseEntity<Object> saved = saveOrbat(admin, sideId, nodes, version);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private Map<String, Object> force(String token, String name) {
        Long themeId = id(created(call(token, HttpMethod.POST, "/api/v1/themes",
                Map.of("name", "Theme " + name, "configJson", Map.of("mode", "dark")))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("themeId", themeId);
        body.put("colour", "#112233");
        return body;
    }

    private ResponseEntity<Object> saveOrbat(String token, Long sideId, List<Map<String, Object>> nodes, long version) {
        return call(token, HttpMethod.PUT, "/api/v1/sides/" + sideId + "/standard-orbat",
                Map.of("orbatJson", Map.of("nodes", nodes), "version", version));
    }

    private static Map<String, Object> nodeTypeBody(String code, String name, Map<String, Object> properties) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("iconPath", "circle.svg");
        if (properties != null) {
            body.put("properties", properties);
        }
        return body;
    }

    private static Map<String, Object> choiceField(String key, String label, boolean required, List<String> options) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", key);
        field.put("label", label);
        field.put("type", "choice");
        field.put("required", required);
        if (options != null) {
            field.put("options", options);
        }
        return field;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> fieldErrors(ResponseEntity<Object> response) {
        return (Map<String, String>) ((Map<?, ?>) response.getBody()).get("fieldErrors");
    }

    private static Map<String, Object> NodeTypeRulesEmpty() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("seeded", false);
        properties.put("root", false);
        properties.put("fields", List.of());
        return properties;
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

    private void assertSituation(String bearer, Long gameId, Long playingOrbatId) {
        Map picture = (Map) call(bearer, HttpMethod.GET, "/api/v1/situation", null).getBody();
        assertThat(((Number) picture.get("gameId")).longValue()).isEqualTo(gameId);
        assertThat(idsOf(picture)).containsExactly(playingOrbatId);
        Map shown = (Map) ((List) picture.get("orbats")).get(0);
        assertThat((List) shown.get("nodes")).isNotEmpty();
    }

    private static List<Long> idsOf(Map picture) {
        return ((List<Map>) picture.get("orbats")).stream()
                .map(item -> ((Number) item.get("playingOrbatId")).longValue())
                .toList();
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
