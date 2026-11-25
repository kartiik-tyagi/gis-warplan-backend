package com.gis.warplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.gis.warplan.domain.ApplicationSlot;
import com.gis.warplan.domain.AssetKind;
import com.gis.warplan.domain.Scenario;
import com.gis.warplan.repository.AssetRepository;
import com.gis.warplan.repository.ScenarioRepository;
import com.gis.warplan.service.AssetManagementService;
import com.gis.warplan.service.AssetService;
import com.gis.warplan.service.ScenarioAttachmentService;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.NotFoundException;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:assetstore;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class AssetStoreTest {

    @Autowired
    private AssetService assets;
    @Autowired
    private AssetManagementService settings;
    @Autowired
    private AssetRepository repository;
    @Autowired
    private ScenarioAttachmentService attachments;
    @Autowired
    private ScenarioRepository scenarios;

    @Test
    void legacyLabelsKeepTheLowestUserId() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("9", Map.of("circle.svg", "Later"));
        nested.put("2", Map.of("circle.svg", "First"));
        Map<String, String> flat = AssetService.flattenLabels(nested);
        assertThat(flat).containsEntry("circle.svg", "First");
    }

    @Test
    void aDocumentIsNotAnIconAndATinyLimitIsEnforced() {
        assertThatThrownBy(() -> assets.storeUpload(AssetKind.ICON, "notes.pdf", "hello".getBytes(), null))
                .isInstanceOf(BadRequestException.class);
        settings.updateLimits(8L, AssetService.DEFAULT_IMAGE_MAX, AssetService.DEFAULT_DOCUMENT_MAX);
        assertThatThrownBy(() -> assets.storeUpload(AssetKind.ICON, "mark.png", "0123456789".getBytes(), null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("8 bytes");
    }

    @Test
    void aPathCannotLeaveTheFolderAndAMissingFileIsNotFound() {
        assertThatThrownBy(() -> assets.readByKey("../secret.txt")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> assets.readByKey("icons/missing.png")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aLabelIsStoredOnTheAssetForEveryone() {
        var stored = assets.storeUpload(AssetKind.DOCUMENT, "brief.txt", "orders".getBytes(), null);
        var labeled = assets.setLabel(stored.getId(), "Orders");
        assertThat(repository.findById(labeled.getId()).orElseThrow().getLabel()).isEqualTo("Orders");
        assertThat(assets.list(AssetKind.DOCUMENT).stream().filter(item -> item.getId().equals(labeled.getId())).findFirst().orElseThrow().displayName())
                .isEqualTo("Orders");
    }

    @Test
    void aMissingApplicationPictureIsNullAndAScenarioFileIsRemovedWithTheScenario() throws Exception {
        assertThat(assets.readApplication(null)).isNull();
        assertThat(assets.applicationUrl(ApplicationSlot.LOGO)).isNotBlank();

        Scenario scenario = new Scenario();
        scenario.setName("Asset scenario");
        scenario = scenarios.save(scenario);
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", "hello".getBytes());
        attachments.store(scenario.getId(), file);
        scenario = scenarios.findById(scenario.getId()).orElseThrow();
        Long assetId = scenario.getAttachmentAssetId();
        assertThat(assetId).isNotNull();
        attachments.purge(scenario.getId());
        assertThat(repository.findById(assetId)).isEmpty();
    }
}
