package com.bookticket.booking_service.repository;

import com.bookticket.booking_service.entity.FailedEvent;
import com.bookticket.booking_service.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailedEventRepository extends JpaRepository<FailedEvent, Long> {
    
    /**
     * Find all events that are pending and haven't exceeded max retries
     */
    List<FailedEvent> findByStatusAndRetryCountLessThan(EventStatus status, Integer maxRetries);
    
    /**
     * Find all failed events for a specific booking
     */
    List<FailedEvent> findByBookingId(Long bookingId);
    
    /**
     * Find all events with FAILED status (exhausted retries)
     */
    List<FailedEvent> findByStatus(EventStatus status);
}

