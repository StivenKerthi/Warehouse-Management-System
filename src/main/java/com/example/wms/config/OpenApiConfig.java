package com.example.wms.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc / Swagger UI configuration.
 *
 * <h2>Authentication in Swagger UI</h2>
 * <ol>
 *   <li>Call {@code POST /api/auth/login} with your credentials.</li>
 *   <li>Copy the {@code token} value from the response body.</li>
 *   <li>Click the <strong>Authorize</strong> button (top-right of Swagger UI).</li>
 *   <li>Paste the token into the <em>Value</em> field — do <strong>not</strong>
 *       add a "Bearer " prefix; Swagger UI adds it automatically.</li>
 *   <li>Click <strong>Authorize</strong>, then <strong>Close</strong>.</li>
 *   <li>All subsequent requests will include {@code Authorization: Bearer <token>}.</li>
 * </ol>
 *
 * <p>The Bearer scheme is applied globally so every endpoint shows the padlock icon
 * without each controller needing to repeat the annotation.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, bearerScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("Warehouse Management System API")
                .version("1.0.0")
                .description("""
                        REST API for the Lufthansa Industry Solutions Warehouse Management System.

                        ## Roles
                        | Role | Access |
                        |------|--------|
                        | `CLIENT` | Manage own orders |
                        | `WAREHOUSE_MANAGER` | Orders, inventory, trucks, delivery scheduling |
                        | `SYSTEM_ADMIN` | User management, system config, SLA reporting |

                        ## Authentication
                        1. `POST /api/auth/login` → copy the `token` from the response.
                        2. Click **Authorize** → paste the token (no `Bearer ` prefix needed).
                        """)
                .contact(new Contact()
                        .name("Admin for technical support")
                        .email("stivenkerthi@gmail.com"));
    }

    /**
     * HTTP Bearer token scheme backed by JWT.
     * The {@code bearerFormat} hint ("JWT") is purely documentary — Swagger UI
     * displays it in the auth dialog but does not validate the token format.
     */
    private SecurityScheme bearerScheme() {
        return new SecurityScheme()
                .name(BEARER_SCHEME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Paste the JWT token obtained from POST /api/auth/login. " +
                             "Do not include the 'Bearer ' prefix — Swagger adds it automatically.");
    }
}
