package com.hospital.crm.his.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HisConfiguration {

    @Bean
    public HisCircuitBreaker hisCircuitBreaker(HisProperties properties) {
        HisProperties.CircuitBreaker breaker = properties.getCircuitBreaker();
        return new HisCircuitBreaker(
                breaker.getFailureThreshold(),
                Math.max(breaker.getOpenSeconds(), 1) * 1000L,
                breaker.getHalfOpenProbes());
    }

    @Bean
    public HisQueryBuilder hisQueryBuilder() {
        return new HisQueryBuilder();
    }
}
