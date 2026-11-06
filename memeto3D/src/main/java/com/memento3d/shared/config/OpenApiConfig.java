
package com.memento3d.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI openAPI() {

        // Relative URL: automatically uses the current Swagger host.
        Server currentServer = new Server()
                .url("/")
                .description("Current Server");

        Contact contact = new Contact()
                .name("Memento3D Team")
                .email("support@memento3d.com");

        License license = new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0.html");

        Info info = new Info()
                .title("Memento3D REST API")
                .description(
                        "API documentation for Memento3D Backend services "
                                + "with JWT authentication and Spring Security."
                )
                .version("1.0.0")
                .contact(contact)
                .license(license);

        SecurityScheme securityScheme = new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description(
                        "Enter your JWT token without the Bearer prefix."
                );

        return new OpenAPI()
                .info(info)
                .servers(List.of(currentServer))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList(SECURITY_SCHEME_NAME)
                )
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        SECURITY_SCHEME_NAME,
                                        securityScheme
                                )
                );
    }
}
