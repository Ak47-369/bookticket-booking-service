package com.bookticket.booking_service.repository;

import com.bookticket.booking_service.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    /**
     * Find all booking seats for a given booking ID
     *
     * @param bookingId Booking ID
     * @return List of booking seats
     */
    List<BookingSeat> findByBookingId(Long bookingId);
}

