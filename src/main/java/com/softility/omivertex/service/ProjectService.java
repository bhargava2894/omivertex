package com.softility.omivertex.service;

import com.softility.omivertex.domain.Client;
import com.softility.omivertex.domain.Project;
import com.softility.omivertex.domain.ProjectStatus;
import com.softility.omivertex.repository.AllocationRepository;
import com.softility.omivertex.repository.ClientRepository;
import com.softility.omivertex.repository.ProjectRepository;
import com.softility.omivertex.web.dto.ProjectRequest;
import com.softility.omivertex.web.dto.ProjectResponse;
import com.softility.omivertex.web.error.ConflictException;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ClientRepository clientRepository;
    private final AllocationRepository allocationRepository;
    private final com.softility.omivertex.repository.OpenPositionRepository openPositionRepository;

    public ProjectService(ProjectRepository projectRepository, ClientRepository clientRepository,
                          AllocationRepository allocationRepository,
                          com.softility.omivertex.repository.OpenPositionRepository openPositionRepository) {
        this.projectRepository = projectRepository;
        this.clientRepository = clientRepository;
        this.allocationRepository = allocationRepository;
        this.openPositionRepository = openPositionRepository;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(Long clientId) {
        List<Project> projects = clientId == null
                ? projectRepository.findAllByOrderByNameAsc()
                : projectRepository.findByClientId(clientId);
        return projects.stream().map(ProjectResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long id) {
        return ProjectResponse.from(find(id));
    }

    public ProjectResponse create(ProjectRequest request) {
        if (projectRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("A project with code '" + request.code() + "' already exists");
        }
        Project project = new Project();
        apply(project, request);
        return ProjectResponse.from(projectRepository.save(project));
    }

    public ProjectResponse update(Long id, ProjectRequest request) {
        Project project = find(id);
        if (!project.getCode().equalsIgnoreCase(request.code())
                && projectRepository.existsByCodeIgnoreCase(request.code())) {
            throw new ConflictException("A project with code '" + request.code() + "' already exists");
        }
        apply(project, request);
        return ProjectResponse.from(project);
    }

    public void delete(Long id) {
        Project project = find(id);
        if (allocationRepository.existsByProjectId(id)) {
            throw new ConflictException("Project has allocations; roll off associates first");
        }
        if (openPositionRepository.existsByProjectId(id)) {
            throw new ConflictException("Project has open positions; close or delete them first");
        }
        projectRepository.delete(project);
    }

    private Project find(Long id) {
        return projectRepository.findById(id).orElseThrow(() -> new NotFoundException("Project", id));
    }

    private void apply(Project project, ProjectRequest request) {
        Client client = clientRepository.findById(request.clientId())
                .orElseThrow(() -> new NotFoundException("Client", request.clientId()));
        project.setCode(request.code());
        project.setName(request.name());
        project.setClient(client);
        project.setStatus(request.status() == null ? ProjectStatus.ACTIVE : request.status());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
    }
}
