package org.example.steelbikerunbackend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("H3-SafeRide API")
                        .description("Ride-hailing backend với H3 Geospatial Indexing & AI Driver Safety")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("SteelBikeRun Team")
                                .email("dev@steelbikerun.com")))
                // Đăng ký global Bearer security scheme
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Nhập JWT token nhận được từ /api/v1/auth/login")));
    }

    @Bean
    public GroupedOpenApi authGroup() {
        return GroupedOpenApi.builder()
                .group("1-auth")
                .displayName("🔐 Authentication")
                .pathsToMatch("/api/v1/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi driverGroup() {
        return GroupedOpenApi.builder()
                .group("2-driver")
                .displayName("🚗 Driver")
                .pathsToMatch("/api/v1/driver/**")
                .build();
    }

    @Bean
    public GroupedOpenApi tripGroup() {
        return GroupedOpenApi.builder()
                .group("3-trip")
                .displayName("🗺️ Trip")
                .pathsToMatch("/api/v1/trip/**")
                .build();
    }

    @Bean
    public GroupedOpenApi userGroup() {
        return GroupedOpenApi.builder()
                .group("4-user")
                .displayName("👤 User")
                .pathsToMatch("/api/v1/user/**")
                .build();
    }
}
