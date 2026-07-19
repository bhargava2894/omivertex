package com.softility.omivertex.web.dto;

import com.softility.omivertex.domain.Client;
import com.softility.omivertex.domain.EntityStatus;

import java.time.Instant;

public record ClientResponse(
        Long id,
        String name,
        String clientId,
        String industry,
        String location,
        EntityStatus status,
        Instant createdAt) {

    public static ClientResponse from(Client client) {
        return new ClientResponse(client.getId(), client.getName(), client.getClientId(),
                client.getIndustry(), client.getLocation(), client.getStatus(), client.getCreatedAt());
    }
}
