package com.softility.omivertex.service;

import com.softility.omivertex.domain.Client;
import com.softility.omivertex.domain.EntityStatus;
import com.softility.omivertex.repository.ClientRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.ClientRequest;
import com.softility.omivertex.web.dto.ClientResponse;
import com.softility.omivertex.web.error.ConflictException;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ClientService {

    private final ClientRepository clientRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    public ClientService(ClientRepository clientRepository, ProjectRepository projectRepository,
                         AuditService auditService) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ClientResponse> list() {
        return clientRepository.findAll().stream().map(ClientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ClientResponse get(Long id) {
        return ClientResponse.from(find(id));
    }

    public ClientResponse create(ClientRequest request) {
        if (clientRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A client named '" + request.name() + "' already exists");
        }
        if (request.clientId() != null && !request.clientId().isBlank()) {
            if (clientRepository.existsByClientIdIgnoreCase(request.clientId().trim())) {
                throw new ConflictException("A client with Client ID '" + request.clientId().trim() + "' already exists");
            }
        }
        Client client = new Client();
        apply(client, request);
        client = clientRepository.save(client);
        auditService.record("CREATED", "Client", client.getId(), "Created client " + client.getName());
        return ClientResponse.from(client);
    }

    public ClientResponse update(Long id, ClientRequest request) {
        Client client = find(id);
        if (!client.getName().equalsIgnoreCase(request.name())
                && clientRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A client named '" + request.name() + "' already exists");
        }
        if (request.clientId() != null && !request.clientId().isBlank()) {
            boolean isNewOrChanged = client.getClientId() == null || !client.getClientId().equalsIgnoreCase(request.clientId().trim());
            if (isNewOrChanged && clientRepository.existsByClientIdIgnoreCase(request.clientId().trim())) {
                throw new ConflictException("A client with Client ID '" + request.clientId().trim() + "' already exists");
            }
        }
        apply(client, request);
        auditService.record("UPDATED", "Client", client.getId(), "Updated client " + client.getName());
        return ClientResponse.from(client);
    }

    public void delete(Long id) {
        Client client = find(id);
        if (projectRepository.existsByClientId(id)) {
            throw new ConflictException("Client has projects; remove or reassign them first");
        }
        clientRepository.delete(client);
        auditService.record("DELETED", "Client", id, "Deleted client " + client.getName());
    }

    private Client find(Long id) {
        return clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client", id));
    }

    private void apply(Client client, ClientRequest request) {
        client.setName(request.name());
        client.setClientId(request.clientId() != null && !request.clientId().isBlank() ? request.clientId().trim() : null);
        client.setIndustry(request.industry());
        client.setLocation(request.location());
        client.setStatus(request.status() == null ? EntityStatus.ACTIVE : request.status());
    }
}
