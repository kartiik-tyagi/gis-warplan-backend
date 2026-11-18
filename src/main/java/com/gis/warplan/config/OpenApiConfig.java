package com.gis.warplan.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI warplanOpenApi(WarplanProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title("GIS Warplan")
                        .version("0.1.0")
                        .description("GIS Warplan API. Operations are served under " + properties.getApiBasePath() + "."))
                .addServersItem(new Server().url("/").description("GIS Warplan"));
    }
}
