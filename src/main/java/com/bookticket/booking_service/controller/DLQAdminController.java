package com.bookticket.booking_service.controller;

import com.bookticket.booking_service.dto.BookingDLQStats;
import com.bookticket.booking_service.dto.DLQStats;
import com.bookticket.booking_service.entity.FailedEvent;
import com.bookticket.booking_service.enums.EventStatus;
import com.bookticket.booking_service.service.DeadLetterQueueService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin controller for managing Dead Letter Queue
 * Provides endpoints to view and manage failed events
 */
@RestController
@RequestMapping("/api/v1/admin/booking-dlq")
@Slf4j
public class DLQAdminController {
    
    private final DeadLetterQueueService dlqService;
    
    public DLQAdminController(DeadLetterQueueService dlqService) {
        this.dlqService = dlqService;
    }
    
    /**
     * Get all pending events in DLQ
     * These are events that can still be retried
     */
    @GetMapping("/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FailedEvent>> getPendingEvents() {
        log.info("Admin request: Get pending DLQ events");
        List<FailedEvent> events = dlqService.getPendingEvents();
        return ResponseEntity.ok(events);
    }
    
    /**
     * Get all permanently failed events
     * These have exhausted all retry attempts
     */
    @GetMapping("/failed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FailedEvent>> getFailedEvents() {
        log.info("Admin request: Get permanently failed DLQ events");
        List<FailedEvent> events = dlqService.getFailedEvents();
        return ResponseEntity.ok(events);
    }

    /**
     * Get DLQ statistics for a specific booking ID
     * Returns all failed events and their statuses for the given booking
     */
    @GetMapping("/booking/{bookingId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BookingDLQStats> getStatsByBookingId(@PathVariable Long bookingId) {
        log.info("Admin request: Get DLQ statistics for booking {}", bookingId);

        List<FailedEvent> events = dlqService.getFailedEventsByBookingId(bookingId);

        if (events.isEmpty()) {
            return ResponseEntity.ok(new BookingDLQStats(
                    bookingId,
                    0,
                    0,
                    0,
                    0,
                    events
            ));
        }

        long pendingCount = events.stream()
                .filter(e -> e.getStatus() == EventStatus.PENDING)
                .count();

        long retryingCount = events.stream()
                .filter(e -> e.getStatus() == EventStatus.RETRYING)
                .count();

        long failedCount = events.stream()
                .filter(e -> e.getStatus() == EventStatus.FAILED)
                .count();

        long processedCount = events.stream()
                .filter(e -> e.getStatus() == EventStatus.PROCESSED)
                .count();

        BookingDLQStats stats = new BookingDLQStats(
                bookingId,
                (int) pendingCount,
                (int) retryingCount,
                (int) failedCount,
                (int) processedCount,
                events
        );

        return ResponseEntity.ok(stats);
    }
    
    /**
     * Manually mark an event as processed
     * Use this after manually fixing the issue
     */
    @PostMapping("/{eventId}/mark-processed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> markAsProcessed(@PathVariable Long eventId) {
        log.info("Admin request: Mark DLQ event {} as processed", eventId);
        dlqService.markAsProcessed(eventId);
        return ResponseEntity.ok("Event marked as processed");
    }
    
    /**
     * Get event statistics
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DLQStats> getStats() {
        log.info("Admin request: Get DLQ statistics");

        List<FailedEvent> pending = dlqService.getPendingEvents();
        List<FailedEvent> failed = dlqService.getFailedEvents();

        DLQStats stats = new DLQStats(
                pending.size(),
                failed.size(),
                pending.size() + failed.size()
        );
        return ResponseEntity.ok(stats);
    }
}

