package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.AccessStatus;
import com.softility.omivertex.domain.AppUser;
import com.softility.omivertex.domain.Role;

import java.time.Instant;

public record AccessRequestResponse(
        Long id,
        String email,
        String name,
        Role role,
        AccessStatus status,
        Instant createdAt) {

    public static AccessRequestResponse from(AppUser u) {
        return new AccessRequestResponse(u.getId(), u.getEmail(), u.getName(),
                u.getRole(), u.getStatus(), u.getCreatedAt());
    }
}
