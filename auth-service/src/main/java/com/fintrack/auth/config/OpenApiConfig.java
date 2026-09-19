package com.fintrack.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * SpringDoc OpenAPI (Swagger UI) configuration for the Auth Service.
 *
 * <p>Swagger UI is available at:
 * <ul>
 *   <li>{@code http://localhost:8081/swagger-ui.html} — interactive UI</li>
 *   <li>{@code http://localhost:8081/v3/api-docs}     — raw OpenAPI JSON spec</li>
 * </ul>
 *
 */
@Configuration
public class OpenApiConfig {

    /** Security scheme name — referenced by {@code @SecurityRequirement} on controllers. */
    public static final String SECURITY_SCHEME_NAME = "Bearer Authentication";

    @Value("${server.port:8081}")
    private String serverPort;

    @Bean
    public OpenAPI fintrackAuthOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinTrack – Auth Service API")
                        .description("""
                                Authentication and Authorization API for FinTrack.
                                
                                **How to authenticate:**
                                1. Use `POST /api/v1/auth/login` to obtain an access token
                                2. Click the **Authorize** 🔒 button above
                                3. Enter: `Bearer <your-access-token>`
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("FinTrack Team")
                                .email("dev@fintrack.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT"))
                )
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server")
                ))
                .addSecurityItem(new SecurityRequirement()
                        .addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT access token here (without the 'Bearer ' prefix)")
                        )
                );
    }
}

