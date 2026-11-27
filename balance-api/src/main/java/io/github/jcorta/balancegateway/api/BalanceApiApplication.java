package io.github.jcorta.balancegateway.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BalanceApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BalanceApiApplication.class, args);
    }
}
