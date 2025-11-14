package com.bookticket.booking_service.configuration;

import com.bookticket.booking_service.security.HeaderPropagationInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {
    @Bean
    public HeaderPropagationInterceptor headerPropagationInterceptor() {
        return new HeaderPropagationInterceptor();
    }

    @Bean("theaterApiClient")
    @LoadBalanced
    public RestClient.Builder theaterRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient theaterRestClient(RestClient.Builder theaterRestClientBuilder) {
        return theaterRestClientBuilder
                .baseUrl("lb://theater-service")
                .requestInterceptor(headerPropagationInterceptor())
                .build();
    }
}
