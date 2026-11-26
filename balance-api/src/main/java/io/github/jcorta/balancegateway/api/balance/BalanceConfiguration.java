package io.github.jcorta.balancegateway.api.balance;

import com.github.benmanes.caffeine.cache.Ticker;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BalanceConfiguration {

    @Bean
    LastKnownBalanceCache lastKnownBalanceCache(FallbackProperties properties) {
        return new LastKnownBalanceCache(properties.maxAge(), properties.maxEntries(), Ticker.systemTicker());
    }
}
