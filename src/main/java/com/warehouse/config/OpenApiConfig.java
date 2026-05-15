package com.warehouse.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Springdoc OpenAPI yapılandırması.
 *
 * <p>Swagger UI: {@code /swagger-ui.html} — admin-only erişim (SecurityConfig
 * tarafından korunmaz; ileride genel actuator gibi ADMIN role'ünün arkasına
 * alınabilir). API dokümantasyonu otomatik üretilir, ek annotation gerekmez
 * (controller method'larının @PostMapping, @RequestBody vb. annotations'ları
 * yeterli).</p>
 *
 * <p>Production'da Swagger UI kapalı tutulabilir:
 * {@code springdoc.swagger-ui.enabled=false} — admin için güvenlik artar.</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Warehouse Management + E-Commerce API")
                        .version("1.0.0")
                        .description("Depo yönetim + B2C e-ticaret platformu REST API'leri. " +
                                     "Admin endpoint'leri /api/admin/, store endpoint'leri /api/store/ prefix'i altında.")
                        .contact(new Contact()
                                .name("Geliştirici Ekibi")
                                .email("dev@example.com"))
                        .license(new License()
                                .name("Proprietary")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT token (login response'undan alınır)")));
    }
}
