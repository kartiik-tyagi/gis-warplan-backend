package com.gis.warplan;

import com.gis.warplan.config.WarplanProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
@EnableConfigurationProperties(WarplanProperties.class)
public class GisWarplanApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(GisWarplanApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(GisWarplanApplication.class, args);
    }
}
