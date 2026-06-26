package com.example.orderflowmanagement.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI configuration for API documentation.
 * 
 * Interview Talking Points:
 * - Auto-generated API docs available at /swagger-ui.html
 * - REST clients can discover and test endpoints
 * - Integrated with Spring Boot actuator
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI orderFlowXApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OrderFlowX API")
                        .description("Enterprise distributed order processing system with event sourcing and saga patterns")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("OrderFlowX Team")
                                .url("https://github.com/orderflowx"))
                        .license(new License()
                                .name("MIT")
                                .url("https://opensource.org/licenses/MIT")));
    }
}
