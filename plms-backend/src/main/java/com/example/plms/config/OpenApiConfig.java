package com.example.plms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI document served at /v3/api-docs (Swagger UI at /swagger-ui.html). */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI plmsOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Personal Loan Management System API").version("v1")
                        .description("Training reference. Synthetic data and simulated financial integrations only."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
