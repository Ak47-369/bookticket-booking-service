package com.bookticket.booking_service.service;

import com.bookticket.booking_service.entity.FailedEvent;
import com.bookticket.booking_service.enums.EventStatus;
import com.bookticket.booking_service.enums.EventType;
import com.bookticket.booking_service.repository.FailedEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service to manage Dead Letter Queue (DLQ) for failed booking events
 * Stores events that fail to send via Kafka and REST fallback
 */
@Service
@Slf4j
public class DeadLetterQueueService {
    
    private final FailedEventRepository failedEventRepository;
    private final ObjectMapper objectMapper;
    
    public DeadLetterQueueService(FailedEventRepository failedEventRepository, ObjectMapper objectMapper) {
        this.failedEventRepository = failedEventRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Store a failed booking success event in DLQ
     */
    @Transactional
    public void storeFailedSuccessEvent(Long bookingId, Long userId, Long showId, 
                                       Double totalAmount, String errorMessage) {
        try {
            FailedEvent failedEvent = new FailedEvent();
            failedEvent.setEventType(EventType.BOOKING_SUCCESS);
            failedEvent.setBookingId(bookingId);
            failedEvent.setUserId(userId);
            failedEvent.setShowId(showId);
            failedEvent.setTotalAmount(totalAmount);
            failedEvent.setStatus(EventStatus.PENDING);
            failedEvent.setLastError(truncateError(errorMessage));
            
            // Store event as JSON for later replay
            String payload = String.format(
                "{\"bookingId\":%d,\"userId\":%d,\"showId\":%d,\"totalAmount\":%.2f}",
                bookingId, userId, showId, totalAmount
            );
            failedEvent.setEventPayload(payload);
            
            failedEventRepository.save(failedEvent);
            log.warn("Stored failed BOOKING_SUCCESS event in DLQ for booking {}", bookingId);
            
        } catch (Exception e) {
            log.error("Failed to store event in DLQ for booking {}: {}", bookingId, e.getMessage(), e);
        }
    }
    
    /**
     * Store a failed booking failed event in DLQ
     */
    @Transactional
    public void storeFailedFailureEvent(Long bookingId, Long userId, Long showId, 
                                       Double totalAmount, String reason, String errorMessage) {
        try {
            FailedEvent failedEvent = new FailedEvent();
            failedEvent.setEventType(EventType.BOOKING_FAILED);
            failedEvent.setBookingId(bookingId);
            failedEvent.setUserId(userId);
            failedEvent.setShowId(showId);
            failedEvent.setTotalAmount(totalAmount);
            failedEvent.setReason(reason);
            failedEvent.setStatus(EventStatus.PENDING);
            failedEvent.setLastError(truncateError(errorMessage));
            
            // Store event as JSON for later replay
            String payload = String.format(
                "{\"bookingId\":%d,\"userId\":%d,\"showId\":%d,\"totalAmount\":%.2f,\"reason\":\"%s\"}",
                bookingId, userId, showId, totalAmount, reason != null ? reason.replace("\"", "\\\"") : ""
            );
            failedEvent.setEventPayload(payload);
            
            failedEventRepository.save(failedEvent);
            log.warn("Stored failed BOOKING_FAILED event in DLQ for booking {}", bookingId);
            
        } catch (Exception e) {
            log.error("Failed to store event in DLQ for booking {}: {}", bookingId, e.getMessage(), e);
        }
    }
    
    /**
     * Get all pending events that can be retried
     */
    public List<FailedEvent> getPendingEvents() {
        return failedEventRepository.findByStatusAndRetryCountLessThan(EventStatus.PENDING, 3);
    }
    
    /**
     * Get all permanently failed events (exhausted retries)
     */
    public List<FailedEvent> getFailedEvents() {
        return failedEventRepository.findByStatus(EventStatus.FAILED);
    }
    
    /**
     * Mark event as successfully processed
     */
    @Transactional
    public void markAsProcessed(Long eventId) {
        failedEventRepository.findById(eventId).ifPresent(event -> {
            event.setStatus(EventStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            failedEventRepository.save(event);
            log.info("Marked DLQ event {} as PROCESSED", eventId);
        });
    }
    
    /**
     * Increment retry count and update last error
     */
    @Transactional
    public void incrementRetryCount(Long eventId, String errorMessage) {
        failedEventRepository.findById(eventId).ifPresent(event -> {
            event.setRetryCount(event.getRetryCount() + 1);
            event.setLastRetryAt(LocalDateTime.now());
            event.setLastError(truncateError(errorMessage));
            
            // If max retries reached, mark as FAILED
            if (event.getRetryCount() >= event.getMaxRetries()) {
                event.setStatus(EventStatus.FAILED);
                log.error("DLQ event {} has exhausted all retries. Marking as FAILED", eventId);
            }
            
            failedEventRepository.save(event);
        });
    }
    
    /**
     * Truncate error message to fit in database column
     */
    private String truncateError(String error) {
        if (error == null) return null;
        return error.length() > 2000 ? error.substring(0, 2000) : error;
    }

    /**
     * Get failed events by booking ID
     */
    public List<FailedEvent> getFailedEventsByBookingId(Long bookingId) {
        return failedEventRepository.findByBookingId(bookingId);
    }
}

