package io.github.jcorta.balancegateway.coremock;

import java.time.Clock;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreMockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    // java.util.Random lives in java.base; RandomGenerator.getDefault() needs jdk.random, which slim JREs omit.
    RandomGenerator randomGenerator() {
        return new Random();
    }
}
