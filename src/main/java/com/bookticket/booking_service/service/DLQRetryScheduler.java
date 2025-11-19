package com.bookticket.booking_service.service;

import com.bookticket.booking_service.dto.BookingFailedEvent;
import com.bookticket.booking_service.dto.BookingSuccessEvent;
import com.bookticket.booking_service.entity.FailedEvent;
import com.bookticket.booking_service.enums.EventType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Scheduled service to automatically retry failed events from Dead Letter Queue
 * Runs every 5 minutes to process pending events
 */
@Service
@Slf4j
public class DLQRetryScheduler {
    
    private final DeadLetterQueueService dlqService;
    private final KafkaTemplate<String, BookingSuccessEvent> kafkaSuccessTemplate;
    private final KafkaTemplate<String, BookingFailedEvent> kafkaFailedTemplate;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    
    public DLQRetryScheduler(DeadLetterQueueService dlqService,
                            KafkaTemplate<String, BookingSuccessEvent> kafkaSuccessTemplate,
                            KafkaTemplate<String, BookingFailedEvent> kafkaFailedTemplate,
                            NotificationService notificationService,
                            ObjectMapper objectMapper) {
        this.dlqService = dlqService;
        this.kafkaSuccessTemplate = kafkaSuccessTemplate;
        this.kafkaFailedTemplate = kafkaFailedTemplate;
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Scheduled job to retry failed events
     * Runs every 5 minutes (300000 ms)
     * Initial delay of 1 minute to allow application to fully start
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void retryFailedEvents() {
        log.info("Starting DLQ retry job...");
        
        List<FailedEvent> pendingEvents = dlqService.getPendingEvents();
        
        if (pendingEvents.isEmpty()) {
            log.info("No pending events in DLQ to retry");
            return;
        }
        
        log.info("Found {} pending events in DLQ to retry", pendingEvents.size());
        
        for (FailedEvent event : pendingEvents) {
            try {
                retryEvent(event);
            } catch (Exception e) {
                log.error("Error retrying DLQ event {}: {}", event.getId(), e.getMessage(), e);
            }
        }
        
        log.info("DLQ retry job completed");
    }
    
    /**
     * Retry a single failed event
     */
    private void retryEvent(FailedEvent event) {
        log.info("Retrying DLQ event {} (type: {}, booking: {}, attempt: {}/{})",
                event.getId(), event.getEventType(), event.getBookingId(), 
                event.getRetryCount() + 1, event.getMaxRetries());
        
        try {
            if (event.getEventType() == EventType.BOOKING_SUCCESS) {
                retryBookingSuccessEvent(event);
            } else if (event.getEventType() == EventType.BOOKING_FAILED) {
                retryBookingFailedEvent(event);
            }
            
            // If successful, mark as processed
            dlqService.markAsProcessed(event.getId());
            log.info("Successfully processed DLQ event {}", event.getId());
            
        } catch (Exception e) {
            // Increment retry count and update error
            dlqService.incrementRetryCount(event.getId(), e.getMessage());
            log.error("Failed to retry DLQ event {}: {}", event.getId(), e.getMessage());
        }
    }
    
    /**
     * Retry booking success event
     */
    private void retryBookingSuccessEvent(FailedEvent event) throws Exception {
        BookingSuccessEvent successEvent = new BookingSuccessEvent(
                event.getBookingId(),
                event.getUserId(),
                event.getShowId(),
                event.getTotalAmount()
        );
        
        try {
            // Try Kafka first
            kafkaSuccessTemplate.send("booking_success", successEvent);
            log.info("Successfully sent booking success event to Kafka for DLQ event {}", event.getId());
        } catch (Exception e) {
            // Try REST fallback
            log.info("Kafka failed for DLQ event {}, trying REST fallback", event.getId());
            notificationService.sendBookingSuccessEvent(successEvent);
            log.info("Successfully sent booking success event via REST for DLQ event {}", event.getId());
        }
    }
    
    /**
     * Retry booking failed event
     */
    private void retryBookingFailedEvent(FailedEvent event) throws Exception {
        BookingFailedEvent failedEvent = new BookingFailedEvent(
                event.getBookingId(),
                event.getUserId(),
                event.getShowId(),
                event.getTotalAmount(),
                event.getReason()
        );
        
        try {
            // Try Kafka first
            kafkaFailedTemplate.send("booking_failed", failedEvent);
            log.info("Successfully sent booking failed event to Kafka for DLQ event {}", event.getId());
        } catch (Exception e) {
            // Try REST fallback
            log.info("Kafka failed for DLQ event {}, trying REST fallback", event.getId());
            notificationService.sendBookingFailedEvent(failedEvent);
            log.info("Successfully sent booking failed event via REST for DLQ event {}", event.getId());
        }
    }
}

