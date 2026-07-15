package com.softility.omivertex.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.softility.omivertex.domain.Associate;
import com.softility.omivertex.domain.ProfileChangeRequest;
import com.softility.omivertex.domain.ProfileChangeStatus;
import com.softility.omivertex.domain.ProfileChangeType;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.ProfileChangeRequestRepository;
import com.softility.omivertex.repository.SkillRepository;
import com.softility.omivertex.web.dto.ProfileChangeResponse;
import com.softility.omivertex.web.dto.SkillAssignmentRequest;
import com.softility.omivertex.web.error.BadRequestException;
import com.softility.omivertex.web.error.ConflictException;
import com.softility.omivertex.web.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Associate-proposed profile edits (skills / resume). Submissions stay PENDING
 * and touch nothing; an admin decision applies them through the existing
 * services so every current rule (validation, headline derivation, audit) fires.
 */
@Service
@Transactional
public class ProfileChangeService {

    private final ProfileChangeRequestRepository repository;
    private final AssociateRepository associateRepository;
    private final SkillRepository skillRepository;
    private final AssociateService associateService;
    private final ResumeService resumeService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ProfileChangeService(ProfileChangeRequestRepository repository,
                                AssociateRepository associateRepository,
                                SkillRepository skillRepository,
                                AssociateService associateService,
                                ResumeService resumeService,
                                AuditService auditService,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.associateRepository = associateRepository;
        this.skillRepository = skillRepository;
        this.associateService = associateService;
        this.resumeService = resumeService;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public ProfileChangeResponse submitSkills(Long associateId, SkillAssignmentRequest request) {
        assertNoPending(associateId, ProfileChangeType.SKILLS);
        for (var entry : request.skills()) {
            skillRepository.findById(entry.skillId())
                    .orElseThrow(() -> new NotFoundException("Skill", entry.skillId()));
        }
        ProfileChangeRequest change = base(associateId, ProfileChangeType.SKILLS);
        try {
            change.setSkillsPayload(objectMapper.writeValueAsString(request));
        } catch (JsonProcessingException e) {
            throw new BadRequestException("Could not read the proposed skills");
        }
        change = repository.save(change);
        auditService.record("SUBMITTED", "ProfileChange", change.getId(),
                change.getAssociate().getName() + " proposed a skills update");
        return toResponse(change);
    }

    public ProfileChangeResponse submitResume(Long associateId, MultipartFile file) {
        assertNoPending(associateId, ProfileChangeType.RESUME);
        ProfileChangeRequest change = base(associateId, ProfileChangeType.RESUME);
        try {
            change.setResumeFilename(file.getOriginalFilename());
            change.setResumeContentType(file.getContentType());
            change.setResumeByteSize(file.getSize());
            change.setResumeContent(file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Could not read the uploaded file");
        }
        change = repository.save(change);
        auditService.record("SUBMITTED", "ProfileChange", change.getId(),
                change.getAssociate().getName() + " proposed a resume update");
        return toResponse(change);
    }

    @Transactional(readOnly = true)
    public List<ProfileChangeResponse> listForAssociate(Long associateId) {
        return repository.findByAssociateIdOrderByCreatedAtDesc(associateId).stream()
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileChangeResponse> list(ProfileChangeStatus status) {
        return repository.findAllByStatus(status).stream().map(this::toResponse).toList();
    }

    public ProfileChangeResponse approve(Long id, String decidedBy) {
        ProfileChangeRequest change = findPending(id);
        if (change.getType() == ProfileChangeType.SKILLS) {
            associateService.replaceSkills(change.getAssociate().getId(), parseSkills(change));
        } else {
            resumeService.store(change.getAssociate().getId(), change.getResumeFilename(),
                    change.getResumeContentType(), change.getResumeContent());
        }
        change.setStatus(ProfileChangeStatus.APPROVED);
        change.setDecidedBy(decidedBy);
        change.setDecidedAt(Instant.now());
        auditService.record("APPROVED", "ProfileChange", change.getId(),
                "Approved " + change.getType() + " change for " + change.getAssociate().getName());
        return toResponse(change);
    }

    public ProfileChangeResponse reject(Long id, String note, String decidedBy) {
        ProfileChangeRequest change = findPending(id);
        change.setStatus(ProfileChangeStatus.REJECTED);
        change.setNote(note);
        change.setDecidedBy(decidedBy);
        change.setDecidedAt(Instant.now());
        auditService.record("REJECTED", "ProfileChange", change.getId(),
                "Rejected " + change.getType() + " change for " + change.getAssociate().getName());
        return toResponse(change);
    }

    private ProfileChangeRequest findPending(Long id) {
        ProfileChangeRequest change = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Profile change", id));
        if (change.getStatus() != ProfileChangeStatus.PENDING) {
            throw new ConflictException("This change was already decided");
        }
        return change;
    }

    private void assertNoPending(Long associateId, ProfileChangeType type) {
        if (repository.existsByAssociateIdAndTypeAndStatus(associateId, type, ProfileChangeStatus.PENDING)) {
            throw new ConflictException("A " + type.name().toLowerCase()
                    + " change is already awaiting approval — wait for the admin decision");
        }
    }

    private ProfileChangeRequest base(Long associateId, ProfileChangeType type) {
        Associate associate = associateRepository.findById(associateId)
                .orElseThrow(() -> new NotFoundException("Associate", associateId));
        ProfileChangeRequest change = new ProfileChangeRequest();
        change.setAssociate(associate);
        change.setType(type);
        return change;
    }

    private SkillAssignmentRequest parseSkills(ProfileChangeRequest change) {
        try {
            return objectMapper.readValue(change.getSkillsPayload(), SkillAssignmentRequest.class);
        } catch (JsonProcessingException e) {
            throw new ConflictException("Stored change payload is unreadable");
        }
    }

    /**
     * Tolerant read for the list/preview path: returns {@code null} for a non-SKILLS
     * change or an unreadable payload instead of throwing, so one bad row can never
     * 409 the entire admin queue. Approval still uses the strict {@link #parseSkills}.
     */
    private SkillAssignmentRequest readSkillsQuietly(ProfileChangeRequest change) {
        if (change.getType() != ProfileChangeType.SKILLS || change.getSkillsPayload() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(change.getSkillsPayload(), SkillAssignmentRequest.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ProfileChangeResponse toResponse(ProfileChangeRequest change) {
        List<ProfileChangeResponse.ProposedSkill> proposed = List.of();
        // Read tolerantly: a single legacy/malformed payload must degrade to an empty
        // preview, never throw and blank the whole admin queue. Approval stays strict
        // (parseSkills) so a genuinely corrupt change still cannot be silently applied.
        SkillAssignmentRequest skills = readSkillsQuietly(change);
        if (skills != null) {
            proposed = skills.skills().stream()
                    .map(entry -> new ProfileChangeResponse.ProposedSkill(
                            skillRepository.findById(entry.skillId())
                                    .map(s -> s.getName()).orElse("skill #" + entry.skillId()),
                            entry.proficiency(), entry.primary()))
                    .toList();
        }
        return new ProfileChangeResponse(change.getId(),
                change.getAssociate().getId(), change.getAssociate().getName(),
                change.getType(), change.getStatus(), proposed,
                change.getResumeFilename(), change.getResumeByteSize(),
                change.getNote(), change.getCreatedAt(), change.getDecidedAt(), change.getDecidedBy());
    }
}
