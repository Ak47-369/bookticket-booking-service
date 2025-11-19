package com.bookticket.booking_service.entity;

import com.bookticket.booking_service.enums.EventStatus;
import com.bookticket.booking_service.enums.EventType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity to store failed booking events for Dead Letter Queue (DLQ)
 * When Kafka and REST fallback both fail, events are stored here for manual retry
 */
@Entity
@Table(name = "failed_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FailedEvent {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventType eventType;
    
    @Column(nullable = false)
    private Long bookingId;
    
    @Column(nullable = false)
    private Long userId;
    
    @Column(nullable = false)
    private Long showId;
    
    @Column(nullable = false)
    private Double totalAmount;
    
    @Column(length = 1000)
    private String reason;  // For BOOKING_FAILED events
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String eventPayload;  // JSON representation of the event
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.PENDING;
    
    @Column(nullable = false)
    private Integer retryCount = 0;
    
    @Column(nullable = false)
    private Integer maxRetries = 3;
    
    @Column(length = 2000)
    private String lastError;  // Last error message
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    @Column
    private LocalDateTime lastRetryAt;
    
    @Column
    private LocalDateTime processedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

