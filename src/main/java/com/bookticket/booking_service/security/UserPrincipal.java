package com.bookticket.booking_service.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Custom principal class to hold authenticated user information
 * This is stored in the SecurityContext and can be accessed via @AuthenticationPrincipal
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPrincipal implements Serializable {
    private Long userId;
    private String username;
}

