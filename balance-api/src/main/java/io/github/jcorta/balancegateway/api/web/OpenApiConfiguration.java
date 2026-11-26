package io.github.jcorta.balancegateway.api.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI balanceGatewayOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Balance Gateway API")
                .version("v1")
                .description("Balance enquiries for channel systems, resilient against core banking failures."));
    }
}
