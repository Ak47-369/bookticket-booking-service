package com.bookticket.booking_service.service;

import com.bookticket.booking_service.dto.CreateBookingRequest;
import com.bookticket.booking_service.entity.Booking;
import com.bookticket.booking_service.enums.BookingStatus;
import com.bookticket.booking_service.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BookingService {
    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public void createBooking(Long userId,CreateBookingRequest createBookingRequest) {
        log.info("Creating.... Booking For user Id: {}", userId);
        Booking booking = new Booking();
        booking.setUserId(userId);
        booking.setShowId(createBookingRequest.showId());
        booking.setTotalAmount(100.0);
        booking.setStatus(BookingStatus.PENDING);
        Booking createdBooking = bookingRepository.save(booking);
    }
}
