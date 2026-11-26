package io.github.jcorta.balancegateway.api.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    public static final String SECURITY_SCHEME = "clientCredentials";

    @Bean
    OpenAPI balanceGatewayOpenApi(@Value("${balance.openapi.token-url}") String tokenUrl) {
        SecurityScheme clientCredentials = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .description("OAuth 2.0 client credentials. Request a token for the registered client and send it "
                        + "as a bearer token.")
                .flows(new OAuthFlows().clientCredentials(new OAuthFlow()
                        .tokenUrl(tokenUrl)
                        .scopes(new Scopes().addString("balance.read", "Read account balances"))));
        return new OpenAPI()
                .info(new Info()
                        .title("Balance Gateway API")
                        .version("v1")
                        .description("Balance enquiries for channel systems, resilient against core banking failures."))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, clientCredentials));
    }
}
