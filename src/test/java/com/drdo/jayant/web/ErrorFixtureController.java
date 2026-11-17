package com.drdo.jayant.web;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/foundation-check")
public class ErrorFixtureController {

    @PostMapping("/names")
    public Map<String, String> names(@Valid @RequestBody NameRequest request) {
        return Map.of("name", request.name());
    }

    @GetMapping("/broken")
    public String broken() {
        throw new IllegalStateException("select * from secret");
    }

    public record NameRequest(@NotBlank String name) {
    }
}
