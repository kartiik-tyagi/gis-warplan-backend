package com.drdo.jayant.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.drdo.jayant.domain.Game;
import com.drdo.jayant.domain.Scenario;
import com.drdo.jayant.service.ScenarioService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Scenarios need MANAGE_SCENARIO, games MANAGE_GAME (checked in the service). */
@RestController
@RequestMapping("/api/v1")
public class ScenarioController {

    private final ScenarioService scenarios;

    public ScenarioController(ScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @GetMapping("/scenarios")
    public List<ScenarioResponse> list() {
        return scenarios.listScenarios().stream().map(ScenarioResponse::of).toList();
    }

    @GetMapping("/scenarios/{id}")
    public ScenarioResponse get(@PathVariable Long id) {
        return ScenarioResponse.of(scenarios.getScenario(id));
    }

    @PostMapping("/scenarios")
    public ResponseEntity<ScenarioResponse> create(@Valid @RequestBody ScenarioRequest request) {
        Scenario scenario = scenarios.createScenario(request.name(), request.description(), request.themeId(),
                request.configJson());
        return ResponseEntity.status(HttpStatus.CREATED).body(ScenarioResponse.of(scenario));
    }

    @PutMapping("/scenarios/{id}")
    public ScenarioResponse update(@PathVariable Long id, @Valid @RequestBody ScenarioRequest request) {
        return ScenarioResponse.of(scenarios.updateScenario(id, request.name(), request.description(),
                request.themeId(), request.configJson()));
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

    @GetMapping("/scenarios/{scenarioId}/games")
    public List<GameResponse> games(@PathVariable Long scenarioId) {
        return scenarios.listGames(scenarioId).stream().map(GameResponse::of).toList();
    }

    @PostMapping("/scenarios/{scenarioId}/games")
    public ResponseEntity<GameResponse> createGame(@PathVariable Long scenarioId,
            @Valid @RequestBody GameRequest request) {
        Game game = scenarios.createGame(scenarioId, request.name(), request.configJson());
        return ResponseEntity.status(HttpStatus.CREATED).body(GameResponse.of(game));
    }

    @GetMapping("/games/{id}")
    public GameResponse getGame(@PathVariable Long id) {
        return GameResponse.of(scenarios.getGame(id));
    }

    @PutMapping("/games/{id}")
    public GameResponse updateGame(@PathVariable Long id, @Valid @RequestBody GameRequest request) {
        return GameResponse.of(scenarios.updateGame(id, request.name(), request.configJson()));
    }

    @DeleteMapping("/games/{id}")
    public ResponseEntity<Void> deleteGame(@PathVariable Long id) {
        scenarios.deleteGame(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/games/{id}/activate")
    public GameResponse activateGame(@PathVariable Long id) {
        return GameResponse.of(scenarios.activateGame(id));
    }

    public record ScenarioRequest(
            @NotBlank @Size(max = 160) String name,
            @Size(max = 1000) String description,
            Long themeId,
            Map<String, Object> configJson) {
    }

    public record ScenarioResponse(Long id, String name, String description, Long themeId, boolean active,
            Map<String, Object> configJson, Instant createdAt, Instant updatedAt) {

        static ScenarioResponse of(Scenario scenario) {
            return new ScenarioResponse(scenario.getId(), scenario.getName(), scenario.getDescription(),
                    scenario.getThemeId(), scenario.isActive(), scenario.getConfigJson(), scenario.getCreatedAt(),
                    scenario.getUpdatedAt());
        }
    }

    public record GameRequest(@NotBlank @Size(max = 160) String name, Map<String, Object> configJson) {
    }

    public record GameResponse(Long id, String name, Long scenarioId, String status, boolean active,
            Map<String, Object> configJson, Instant startedAt, Instant endedAt, Instant createdAt,
            Instant updatedAt) {

        static GameResponse of(Game game) {
            return new GameResponse(game.getId(), game.getName(), game.getScenarioId(), game.getStatus(),
                    game.isActive(), game.getConfigJson(), game.getStartedAt(), game.getEndedAt(),
                    game.getCreatedAt(), game.getUpdatedAt());
        }
    }
}
