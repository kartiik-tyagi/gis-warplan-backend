package com.gis.warplan.web;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.Game;
import com.gis.warplan.domain.MoonPhase;
import com.gis.warplan.domain.Scenario;
import com.gis.warplan.service.AccessService;
import com.gis.warplan.service.AuditService;
import com.gis.warplan.service.ScenarioAttachmentService;
import com.gis.warplan.service.ScenarioAttachmentService.AttachmentContent;
import com.gis.warplan.service.ScenarioService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Scenarios need MANAGE_SCENARIO, games MANAGE_GAME (checked in the service). */
@RestController
@RequestMapping("/api/v1")
public class ScenarioController {

    private final ScenarioService scenarios;
    private final ScenarioAttachmentService attachments;
    private final AccessService access;
    private final AuditService audit;

    public ScenarioController(ScenarioService scenarios, ScenarioAttachmentService attachments, AccessService access,
            AuditService audit) {
        this.scenarios = scenarios;
        this.attachments = attachments;
        this.access = access;
        this.audit = audit;
    }

    @GetMapping("/scenarios")
    public List<ScenarioResponse> list() {
        return scenarios.listScenarios().stream().map(ScenarioResponse::of).toList();
    }

    /** Clears every active scenario and game (no open exercise). Literal path before {id}. */
    @PostMapping("/scenarios/deactivate")
    public ResponseEntity<Void> deactivate() {
        scenarios.deactivateAll();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scenarios/{id}")
    public ScenarioResponse get(@PathVariable Long id) {
        return ScenarioResponse.of(scenarios.getScenario(id));
    }

    @PostMapping("/scenarios")
    public ResponseEntity<ScenarioResponse> create(@Valid @RequestBody ScenarioRequest request) {
        Scenario scenario = scenarios.createScenario(request.name(), request.generalIdea(), request.openingNarrative(),
                request.specialIdea());
        return ResponseEntity.status(HttpStatus.CREATED).body(ScenarioResponse.of(scenario));
    }

    @PutMapping("/scenarios/{id}")
    public ScenarioResponse update(@PathVariable Long id, @Valid @RequestBody ScenarioRequest request) {
        return ScenarioResponse.of(scenarios.updateScenario(id, request.name(), request.generalIdea(),
                request.openingNarrative(), request.specialIdea()));
    }

    @DeleteMapping("/scenarios/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scenarios.deleteScenario(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/scenarios/{id}/activate")
    public ScenarioResponse activate(@PathVariable Long id) {
        return ScenarioResponse.of(scenarios.activateScenario(id));
    }

    @PostMapping(path = "/scenarios/{id}/attachment", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadAttachment(@PathVariable Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        scenarios.getScenario(id);
        String stored = attachments.store(id, file);
        audit.record("UPLOAD", "Scenario", id, "Attachment uploaded " + stored, Map.of("file", stored));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("attachedFile", stored));
    }

    @GetMapping("/scenarios/{id}/attachment")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        AttachmentContent content = attachments.read(id);
        String disposition = (content.inline() ? "inline" : "attachment") + "; filename=\"" + content.fileName()
                + "\"";
        return ResponseEntity.ok()
                .contentType(content.mediaType())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-cache")
                .body(content.bytes());
    }

    @DeleteMapping("/scenarios/{id}/attachment")
    public ResponseEntity<Void> deleteAttachment(@PathVariable Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_SCENARIO);
        scenarios.getScenario(id);
        attachments.clear(id);
        audit.record("DELETE", "ScenarioAttachment", id, "Scenario attachment removed", Map.of());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/scenarios/{scenarioId}/games")
    public List<GameResponse> games(@PathVariable Long scenarioId) {
        return scenarios.listGames(scenarioId).stream().map(GameResponse::of).toList();
    }

    @PostMapping("/scenarios/{scenarioId}/games")
    public ResponseEntity<GameResponse> createGame(@PathVariable Long scenarioId,
            @Valid @RequestBody GameRequest request) {
        Game game = scenarios.createGame(scenarioId, request.name(), request.dateTime(), request.sunrise(),
                request.sunset(), request.moonphases(), request.description(), request.metadata());
        return ResponseEntity.status(HttpStatus.CREATED).body(GameResponse.of(game));
    }

    /** Literal path before /games/{id}. */
    @GetMapping("/games/moon-phases")
    public List<MoonPhaseOption> moonPhases() {
        return scenarios.moonPhases().stream().map(MoonPhaseOption::of).toList();
    }

    @GetMapping("/games/{id}")
    public GameResponse getGame(@PathVariable Long id) {
        return GameResponse.of(scenarios.getGame(id));
    }

    @PutMapping("/games/{id}")
    public GameResponse updateGame(@PathVariable Long id, @Valid @RequestBody GameRequest request) {
        return GameResponse.of(scenarios.updateGame(id, request.name(), request.dateTime(), request.sunrise(),
                request.sunset(), request.moonphases(), request.description(), request.metadata()));
    }

    @DeleteMapping("/games/{id}")
    public ResponseEntity<Void> deleteGame(@PathVariable Long id) {
        scenarios.deleteGame(id);
        return ResponseEntity.noContent().build();
    }

    /** Clears every active game (scenario stays). Literal path before {id}. */
    @PostMapping("/games/deactivate")
    public ResponseEntity<Void> deactivateGames() {
        scenarios.deactivateGames();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/games/{id}/activate")
    public GameResponse activateGame(@PathVariable Long id) {
        return GameResponse.of(scenarios.activateGame(id));
    }

    public record ScenarioRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 100000) String generalIdea,
            @Size(max = 100000) String openingNarrative,
            @Size(max = 100000) String specialIdea) {
    }

    public record ScenarioResponse(Long id, String name, String generalIdea, String openingNarrative,
            String specialIdea, boolean active, String attachedFile, Instant createdAt, Instant updatedAt,
            Long createdBy) {

        static ScenarioResponse of(Scenario scenario) {
            return new ScenarioResponse(scenario.getId(), scenario.getName(), scenario.getGeneralIdea(),
                    scenario.getOpeningNarrative(), scenario.getSpecialIdea(), scenario.isActive(),
                    scenario.getAttachedFile(), scenario.getCreatedAt(), scenario.getUpdatedAt(),
                    scenario.getCreatedBy());
        }
    }

    public record GameRequest(
            @NotBlank @Size(max = 160) String name,
            LocalDateTime dateTime,
            LocalTime sunrise,
            LocalTime sunset,
            @Size(max = 40) String moonphases,
            @Size(max = 100000) String description,
            Map<String, Object> metadata) {
    }

    public record GameResponse(Long id, Long scenarioId, String name, LocalDateTime dateTime, LocalTime sunrise,
            LocalTime sunset, String moonphases, String description, boolean active, Map<String, Object> metadata,
            Instant createdAt, Instant updatedAt) {

        static GameResponse of(Game game) {
            MoonPhase phase = game.getMoonphases();
            return new GameResponse(game.getId(), game.getScenarioId(), game.getName(), game.getDateTime(),
                    game.getSunrise(), game.getSunset(), phase == null ? null : phase.name(), game.getDescription(),
                    game.isActive(), game.getMetadata(), game.getCreatedAt(), game.getUpdatedAt());
        }
    }

    public record MoonPhaseOption(String code, String label) {
        static MoonPhaseOption of(MoonPhase phase) {
            return new MoonPhaseOption(phase.name(), phase.label());
        }
    }
}
