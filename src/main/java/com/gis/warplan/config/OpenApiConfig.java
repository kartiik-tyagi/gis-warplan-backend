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
        String name = properties.getApplicationName();
        return new OpenAPI()
                .info(new Info()
                        .title(name)
                        .version("0.1.0")
                        .description(name + " API. Operations are served under " + properties.getApiBasePath() + "."))
                .addServersItem(new Server().url("/").description(name));
    }
}
