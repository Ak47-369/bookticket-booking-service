package com.bookticket.booking_service.service;

import com.bookticket.booking_service.dto.BookingFailedEvent;
import com.bookticket.booking_service.dto.BookingSuccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class NotificationService {
    private final RestClient notificationRestClient;

    public NotificationService(@Qualifier("notificationRestClient") RestClient notificationRestClient) {
        this.notificationRestClient = notificationRestClient;
    }

    public void sendBookingSuccessEvent(BookingSuccessEvent bookingSuccessEvent) {
        try {
            log.info("Sending booking success event for booking {} to notification service", bookingSuccessEvent.bookingId());
            notificationRestClient.post()
                    .uri("/api/v1/internal/notifications/booking-success")
                    .body(bookingSuccessEvent)
                    .retrieve()
                    .body(Void.class);
            log.info("Successfully sent booking success event for booking {}", bookingSuccessEvent.bookingId());
        } catch (Exception e) {
            log.error("Failed to send booking success event for booking {}: {}",
                    bookingSuccessEvent.bookingId(), e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    public void sendBookingFailedEvent(BookingFailedEvent bookingFailedEvent) {
        try {
            log.info("Sending booking failed event for booking {} to notification service", bookingFailedEvent.bookingId());
            notificationRestClient.post()
                    .uri("/api/v1/internal/notifications/booking-failure")
                    .body(bookingFailedEvent)
                    .retrieve()
                    .body(Void.class);
            log.info("Successfully sent booking failed event for booking {}", bookingFailedEvent.bookingId());
        } catch (Exception e) {
            log.error("Failed to send booking failed event for booking {}: {}",
                    bookingFailedEvent.bookingId(), e.getMessage(), e);
        }
    }
}
