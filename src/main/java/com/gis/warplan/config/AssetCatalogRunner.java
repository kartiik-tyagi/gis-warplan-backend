package com.gis.warplan.config;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.gis.warplan.domain.ApplicationSlot;
import com.gis.warplan.service.AssetManagementService;
import com.gis.warplan.service.AssetService;
import com.gis.warplan.service.ScenarioAttachmentService;

/** Registers files that already exist, then fills empty application pictures from the seed. */
@Component
@Order(100)
public class AssetCatalogRunner implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(AssetCatalogRunner.class);

    private final AssetManagementService settings;
    private final AssetService assets;
    private final ScenarioAttachmentService attachments;

    public AssetCatalogRunner(AssetManagementService settings, AssetService assets, ScenarioAttachmentService attachments) {
        this.settings = settings;
        this.assets = assets;
        this.attachments = attachments;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Map<String, String> labels = settings.flattenLabels();
            assets.importLooseIcons(labels);
            assets.labelIfBlank("moon-new.svg", "New moon");
            assets.labelIfBlank("moon-waxing-crescent.svg", "Waxing crescent");
            assets.labelIfBlank("moon-first-quarter.svg", "First quarter");
            assets.labelIfBlank("moon-waxing-gibbous.svg", "Waxing gibbous");
            assets.labelIfBlank("moon-full.svg", "Full moon");
            assets.labelIfBlank("moon-waning-gibbous.svg", "Waning gibbous");
            assets.labelIfBlank("moon-last-quarter.svg", "Last quarter");
            assets.labelIfBlank("moon-waning-crescent.svg", "Waning crescent");
            assets.ensureSeedSlot(ApplicationSlot.LOGO, "/seed/application/logo.svg", "logo.svg");
            assets.ensureSeedSlot(ApplicationSlot.FAVICON, "/seed/application/favicon.svg", "favicon.svg");
            assets.ensureSeedSlot(ApplicationSlot.LOGIN_BACKGROUND, "/seed/application/login-background.svg", "login-background.svg");
            attachments.importLegacy();
        } catch (RuntimeException exception) {
            log.error("asset.catalog_failed reason={}", exception.getMessage(), exception);
        }
    }
}
