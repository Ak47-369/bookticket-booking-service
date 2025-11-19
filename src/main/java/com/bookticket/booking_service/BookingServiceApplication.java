package com.bookticket.booking_service;

import com.bookticket.booking_service.configuration.PaymentPollingProperties;
import com.bookticket.booking_service.configuration.RedisLockProperties;
import com.bookticket.booking_service.configuration.ServiceUrlProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({
        RedisLockProperties.class,
        ServiceUrlProperties.class,
        PaymentPollingProperties.class}
)
@EnableAsync
@EnableScheduling
public class BookingServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingServiceApplication.class, args);
	}

}
