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

    public ClientService(ClientRepository clientRepository, ProjectRepository projectRepository) {
        this.clientRepository = clientRepository;
        this.projectRepository = projectRepository;
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
        Client client = new Client();
        apply(client, request);
        return ClientResponse.from(clientRepository.save(client));
    }

    public ClientResponse update(Long id, ClientRequest request) {
        Client client = find(id);
        if (!client.getName().equalsIgnoreCase(request.name())
                && clientRepository.existsByNameIgnoreCase(request.name())) {
            throw new ConflictException("A client named '" + request.name() + "' already exists");
        }
        apply(client, request);
        return ClientResponse.from(client);
    }

    public void delete(Long id) {
        Client client = find(id);
        if (projectRepository.existsByClientId(id)) {
            throw new ConflictException("Client has projects; remove or reassign them first");
        }
        clientRepository.delete(client);
    }

    private Client find(Long id) {
        return clientRepository.findById(id).orElseThrow(() -> new NotFoundException("Client", id));
    }

    private void apply(Client client, ClientRequest request) {
        client.setName(request.name());
        client.setIndustry(request.industry());
        client.setLocation(request.location());
        client.setStatus(request.status() == null ? EntityStatus.ACTIVE : request.status());
    }
}
