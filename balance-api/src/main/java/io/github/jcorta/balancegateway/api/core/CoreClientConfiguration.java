package io.github.jcorta.balancegateway.api.core;

import java.io.IOException;
import java.net.http.HttpClient;

import io.github.jcorta.balancegateway.api.web.CorrelationIdFilter;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class CoreClientConfiguration {

    @Bean
    RestClient coreRestClient(RestClient.Builder builder, CoreProperties properties) {
        // HTTP/1.1 explicitly: the JDK client would otherwise attempt an h2c upgrade on every cleartext request.
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder
                .baseUrl(properties.baseUrl())
                .requestInterceptor(CoreClientConfiguration::forwardCorrelationId)
                .requestFactory(requestFactory)
                .build();
    }

    private static ClientHttpResponse forwardCorrelationId(HttpRequest request, byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            request.getHeaders().set(CorrelationIdFilter.HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}
