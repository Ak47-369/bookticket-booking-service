package com.bookticket.booking_service.controller;

import com.bookticket.booking_service.dto.BookingDLQStats;
import com.bookticket.booking_service.dto.DLQStats;
import com.bookticket.booking_service.entity.FailedEvent;
import com.bookticket.booking_service.enums.EventStatus;
import com.bookticket.booking_service.service.DeadLetterQueueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/booking-dlq")
@Slf4j
@Tag(name = "Admin - Booking DLQ", description = "APIs for managing the Dead Letter Queue for booking events")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class DLQAdminController {
    
    private final DeadLetterQueueService dlqService;
    
    public DLQAdminController(DeadLetterQueueService dlqService) {
        this.dlqService = dlqService;
    }

    @Operation(
            summary = "Get pending events",
            description = "Retrieves a list of all events in the DLQ with PENDING status that are awaiting retry attempts.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved pending events",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FailedEvent.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
                    @ApiResponse(responseCode = "503", description = "Service unavailable"),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout"),
                    @ApiResponse(responseCode = "429", description = "Too many requests")
            }
    )
    @GetMapping("/pending")
    public ResponseEntity<List<FailedEvent>> getPendingEvents() {
        log.info("Admin request: Get pending DLQ events");
        List<FailedEvent> events = dlqService.getPendingEvents();
        return ResponseEntity.ok(events);
    }

    @Operation(
            summary = "Get permanently failed events",
            description = "Retrieves a list of all events that have exhausted all retry attempts and are marked as FAILED.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved failed events",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = FailedEvent.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
                    @ApiResponse(responseCode = "503", description = "Service unavailable"),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout"),
                    @ApiResponse(responseCode = "429", description = "Too many requests")
            }
    )
    @GetMapping("/failed")
    public ResponseEntity<List<FailedEvent>> getFailedEvents() {
        log.info("Admin request: Get permanently failed DLQ events");
        List<FailedEvent> events = dlqService.getFailedEvents();
        return ResponseEntity.ok(events);
    }

    @Operation(
            summary = "Get DLQ statistics by booking ID",
            description = "Returns a summary and list of all failed events associated with a specific booking ID.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved DLQ stats for the booking",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BookingDLQStats.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
                    @ApiResponse(responseCode = "503", description = "Service unavailable"),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout"),
                    @ApiResponse(responseCode = "429", description = "Too many requests")
            }
    )
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<BookingDLQStats> getStatsByBookingId(
            @Parameter(description = "ID of the booking to retrieve DLQ stats for", required = true)
            @PathVariable Long bookingId) {
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

    @Operation(
            summary = "Mark an event as processed",
            description = "Manually marks a DLQ event as PROCESSED. This is an administrative action to be used after an issue has been resolved manually.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Event successfully marked as processed"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role"),
                    @ApiResponse(responseCode = "404", description = "Event not found"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
                    @ApiResponse(responseCode = "503", description = "Service unavailable"),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout"),
                    @ApiResponse(responseCode = "429", description = "Too many requests")
            }
    )
    @PostMapping("/{eventId}/mark-processed")
    public ResponseEntity<String> markAsProcessed(
            @Parameter(description = "ID of the DLQ event to mark as processed", required = true)
            @PathVariable Long eventId) {
        log.info("Admin request: Mark DLQ event {} as processed", eventId);
        dlqService.markAsProcessed(eventId);
        return ResponseEntity.ok("Event marked as processed");
    }

    @Operation(
            summary = "Get overall DLQ statistics",
            description = "Retrieves a count of all pending, failed, and total events in the booking DLQ.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Successfully retrieved DLQ statistics",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DLQStats.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid JWT token"),
                    @ApiResponse(responseCode = "403", description = "Forbidden - User does not have ADMIN role"),
                    @ApiResponse(responseCode = "500", description = "Internal server error"),
                    @ApiResponse(responseCode = "503", description = "Service unavailable"),
                    @ApiResponse(responseCode = "504", description = "Gateway timeout"),
                    @ApiResponse(responseCode = "429", description = "Too many requests")
            }
    )
    @GetMapping("/stats")
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
