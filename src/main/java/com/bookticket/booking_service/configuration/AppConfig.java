package com.bookticket.booking_service.configuration;

import com.bookticket.booking_service.security.HeaderPropagationInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AppConfig {

    private final ServiceUrlProperties serviceUrlProperties;

    public AppConfig(ServiceUrlProperties serviceUrlProperties) {
        this.serviceUrlProperties = serviceUrlProperties;
    }

    @Bean
    public HeaderPropagationInterceptor headerPropagationInterceptor() {
        return new HeaderPropagationInterceptor();
    }

    /**
     * Single load-balanced RestClient.Builder bean
     * Shared by all service clients (theater, payment, etc.)
     */
    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean("theaterRestClient")
    public RestClient theaterRestClient(RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder
                .baseUrl(serviceUrlProperties.getTheaterUrl())
                .requestInterceptor(headerPropagationInterceptor())
                .build();
    }

    @Bean("paymentRestClient")
    public RestClient paymentRestClient(RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder
                .baseUrl(serviceUrlProperties.getPaymentUrl())
                .requestInterceptor(headerPropagationInterceptor())
                .build();
    }

    @Bean("notificationRestClient")
    public RestClient notificationRestClient(RestClient.Builder loadBalancedRestClientBuilder) {
        return loadBalancedRestClientBuilder
                .baseUrl(serviceUrlProperties.getNotificationUrl())
                .requestInterceptor(headerPropagationInterceptor())
                .build();
    }
}
