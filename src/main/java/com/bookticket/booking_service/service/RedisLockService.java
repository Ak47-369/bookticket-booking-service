package com.bookticket.booking_service.service;

import com.bookticket.booking_service.configuration.RedisLockProperties;
import com.bookticket.booking_service.exception.SeatLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RedisLockService {
    
    private final StringRedisTemplate redisTemplate;
    private final RedisLockProperties lockProperties;
    
    public RedisLockService(StringRedisTemplate redisTemplate, RedisLockProperties lockProperties) {
        this.redisTemplate = redisTemplate;
        this.lockProperties = lockProperties;
    }
    
    /**
     * Attempt to acquire locks for all seats in a show
     * If any lock fails, releases all previously acquired locks and throws SeatLockException
     * 
     * @param showId The show ID
     * @param seatIds List of seat IDs to lock
     * @param bookingId The booking ID that will own these locks
     * @return List of successfully locked seat keys
     * @throws SeatLockException if any seat cannot be locked
     */
    public List<String> acquireSeatsLock(Long showId, List<Long> seatIds, Long bookingId) {
        log.info("Attempting to acquire locks for {} seats in show {} for booking {}", 
                seatIds.size(), showId, bookingId);
        
        List<String> acquiredLocks = new ArrayList<>();
        String lockValue = lockProperties.generateBookingLockValue(bookingId);
        
        try {
            for (Long seatId : seatIds) {
                String lockKey = lockProperties.generateSeatLockKey(showId, seatId);
                
                // Try to acquire lock using SET NX (SET if Not eXists)
                Boolean lockAcquired = redisTemplate.opsForValue()
                        .setIfAbsent(
                                lockKey, 
                                lockValue, 
                                lockProperties.getTtl().toMinutes(), 
                                TimeUnit.MINUTES
                        );
                
                if (Boolean.TRUE.equals(lockAcquired)) {
                    acquiredLocks.add(lockKey);
                    log.debug("Successfully acquired lock for seat {} in show {}: {}", 
                            seatId, showId, lockKey);
                } else {
                    // Lock acquisition failed - seat is already locked by another booking
                    log.warn("Failed to acquire lock for seat {} in show {}. Seat is already locked.", 
                            seatId, showId);
                    
                    // Release all previously acquired locks
                    releaseSeatsLock(acquiredLocks);
                    
                    throw new SeatLockException(
                            String.format("Seats no longer available. Seat %d in show %d is already locked.", 
                                    seatId, showId)
                    );
                }
            }
            
            log.info("Successfully acquired locks for all {} seats in show {} for booking {}", 
                    acquiredLocks.size(), showId, bookingId);
            return acquiredLocks;
            
        } catch (SeatLockException e) {
            // Re-throw SeatLockException as-is
            throw e;
        } catch (Exception e) {
            // Unexpected error - release any acquired locks
            log.error("Unexpected error while acquiring seat locks for show {}: {}", showId, e.getMessage(), e);
            releaseSeatsLock(acquiredLocks);
            throw new SeatLockException("Failed to acquire seat locks due to system error", e);
        }
    }
    
    /**
     * Release locks for the given seat keys
     * 
     * @param lockKeys List of Redis keys to delete
     */
    public void releaseSeatsLock(List<String> lockKeys) {
        if (lockKeys == null || lockKeys.isEmpty()) {
            log.debug("No locks to release");
            return;
        }
        
        try {
            Long deletedCount = redisTemplate.delete(lockKeys);
            log.info("Released {} seat locks out of {} requested", deletedCount, lockKeys.size());
        } catch (Exception e) {
            log.error("Error releasing seat locks: {}", e.getMessage(), e);
            // Don't throw exception here - this is cleanup code
        }
    }
    
    /**
     * Release locks for specific seats in a show
     * 
     * @param showId The show ID
     * @param seatIds List of seat IDs to unlock
     */
    public void releaseSeatsLockByIds(Long showId, List<Long> seatIds) {
        List<String> lockKeys = seatIds.stream()
                .map(seatId -> lockProperties.generateSeatLockKey(showId, seatId))
                .toList();
        
        releaseSeatsLock(lockKeys);
    }
    
    /**
     * Check if a seat is currently locked
     * 
     * @param showId The show ID
     * @param seatId The seat ID
     * @return true if the seat is locked, false otherwise
     */
    public boolean isSeatLocked(Long showId, Long seatId) {
        String lockKey = lockProperties.generateSeatLockKey(showId, seatId);
        Boolean hasKey = redisTemplate.hasKey(lockKey);
        return Boolean.TRUE.equals(hasKey);
    }
    
    /**
     * Get the booking ID that currently holds the lock for a seat
     * 
     * @param showId The show ID
     * @param seatId The seat ID
     * @return The booking ID that holds the lock, or null if not locked
     */
    public String getSeatLockOwner(Long showId, Long seatId) {
        String lockKey = lockProperties.generateSeatLockKey(showId, seatId);
        return redisTemplate.opsForValue().get(lockKey);
    }
}

