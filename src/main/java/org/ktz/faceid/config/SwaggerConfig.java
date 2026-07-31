package org.ktz.faceid.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FaceAuth API")
                        .version("1.0")
                        .description("Face ID service — enrollment, verification, liveness. "
                                + "Internal service, called by the orchestrator. "
                                + "Auth is via the X-User-Id header (trusted from orchestrator) "
                                + "or X-Face-Job-Token for anonymous registration jobs."))
                .addSecurityItem(new SecurityRequirement().addList("X-User-Id"))
                .components(new Components()
                        .addSecuritySchemes("X-User-Id",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-User-Id"))
                        .addSecuritySchemes("X-Face-Job-Token",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-Face-Job-Token")));
    }
}