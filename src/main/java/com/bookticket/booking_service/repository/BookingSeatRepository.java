package com.bookticket.booking_service.repository;

import com.bookticket.booking_service.entity.BookingSeat;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {
}

