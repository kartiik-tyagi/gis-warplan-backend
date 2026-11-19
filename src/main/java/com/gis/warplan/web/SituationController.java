package com.gis.warplan.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gis.warplan.service.SituationService;
import com.gis.warplan.service.SituationService.Picture;

/** Live playing-ORBAT picture for the active game. Any signed-in user may read the part they are allowed to see. */
@RestController
@RequestMapping("/api/v1/situation")
public class SituationController {

    private final SituationService situations;

    public SituationController(SituationService situations) {
        this.situations = situations;
    }

    @GetMapping
    public Picture current() {
        return situations.current();
    }
}
