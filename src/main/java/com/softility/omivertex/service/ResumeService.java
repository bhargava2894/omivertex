package com.softility.omivertex.service;

import com.softility.omivertex.domain.Resume;
import com.softility.omivertex.domain.Skill;
import com.softility.omivertex.repository.AssociateRepository;
import com.softility.omivertex.repository.ResumeRepository;
import com.softility.omivertex.repository.SkillRepository;
import com.softility.omivertex.web.dto.ResumeDtos.ParsedResumeResponse;
import com.softility.omivertex.web.dto.ResumeDtos.ResumeMetaResponse;
import com.softility.omivertex.web.dto.ResumeDtos.SuggestedSkill;
import com.softility.omivertex.web.dto.ResumeDtos.SuggestionSource;
import com.softility.omivertex.web.error.BadRequestException;
import com.softility.omivertex.web.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ResumeService {

    /** AI extraction input cap — keeps prompts bounded for very long resumes. */
    static final int MAX_AI_RESUME_CHARS = 20_000;

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeRepository resumeRepository;
    private final AssociateRepository associateRepository;
    private final ResumeTextExtractor textExtractor;
    private final ResumeSkillMatcher skillMatcher;
    private final AuditService auditService;
    private final GeminiClient geminiClient;
    private final SkillRepository skillRepository;

    public ResumeService(ResumeRepository resumeRepository,
                         AssociateRepository associateRepository,
                         ResumeTextExtractor textExtractor,
                         ResumeSkillMatcher skillMatcher,
                         AuditService auditService,
                         GeminiClient geminiClient,
                         SkillRepository skillRepository) {
        this.resumeRepository = resumeRepository;
        this.associateRepository = associateRepository;
        this.textExtractor = textExtractor;
        this.skillMatcher = skillMatcher;
        this.auditService = auditService;
        this.geminiClient = geminiClient;
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public ParsedResumeResponse parse(MultipartFile file) {
        validateFileType(file);
        try {
            byte[] bytes = file.getBytes();
            String text = textExtractor.extractText(bytes, file.getContentType(), file.getOriginalFilename());
            boolean textExtracted = text != null && !text.isBlank();

            if (textExtracted && geminiClient.isConfigured()) {
                try {
                    return aiParse(text);
                } catch (Exception e) {
                    log.warn("AI resume extraction failed — falling back to keyword matching", e);
                }
            }

            List<Skill> matched = skillMatcher.matchSkills(text);
            List<SuggestedSkill> suggestions = matched.stream()
                    .map(s -> new SuggestedSkill(s.getId(), s.getName(), s.getCategory().getName(), null, null))
                    .collect(Collectors.toList());
            return new ParsedResumeResponse(suggestions, textExtracted, null, SuggestionSource.KEYWORD);
        } catch (IOException e) {
            throw new BadRequestException("Failed to read upload file: " + e.getMessage());
        }
    }

    private ParsedResumeResponse aiParse(String text) {
        Map<Long, Skill> byId = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        List<GeminiClient.SkillOption> taxonomy = byId.values().stream()
                .map(s -> new GeminiClient.SkillOption(s.getId(), s.getName()))
                .toList();
        String capped = text.length() > MAX_AI_RESUME_CHARS ? text.substring(0, MAX_AI_RESUME_CHARS) : text;
        GeminiClient.ResumeExtraction extraction = geminiClient.extractResume(capped, taxonomy);
        List<SuggestedSkill> suggestions = extraction.skills().stream()
                .filter(s -> byId.containsKey(s.skillId()))
                .map(s -> {
                    Skill skill = byId.get(s.skillId());
                    return new SuggestedSkill(skill.getId(), skill.getName(),
                            skill.getCategory().getName(), s.proficiency(), s.evidence());
                })
                .toList();
        return new ParsedResumeResponse(suggestions, true, extraction.experienceSummary(), SuggestionSource.AI);
    }

    public ResumeMetaResponse store(Long associateId, MultipartFile file) {
        validateFileType(file);
        try {
            return store(associateId, file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException e) {
            throw new BadRequestException("Failed to save uploaded résumé: " + e.getMessage());
        }
    }

    /** Same store rules from raw bytes — used when applying an approved profile change. */
    public ResumeMetaResponse store(Long associateId, String filename, String contentType, byte[] bytes) {
        if (!associateRepository.existsById(associateId)) {
            throw new NotFoundException("Associate", associateId);
        }
        boolean exists = resumeRepository.existsByAssociateId(associateId);
        if (exists) {
            resumeRepository.deleteByAssociateId(associateId);
            resumeRepository.flush();
        }

        Resume resume = new Resume();
        resume.setAssociateId(associateId);
        resume.setFilename(filename);
        resume.setContentType(contentType);
        resume.setByteSize((long) bytes.length);
        resume.setContent(bytes);
        resume.setUploadedAt(Instant.now());
        resumeRepository.save(resume);

        String auditMsg = exists ? "Replaced résumé: " + filename : "Uploaded résumé: " + filename;
        auditService.record("UPDATED", "Associate", associateId, auditMsg);

        return new ResumeMetaResponse(filename, contentType, (long) bytes.length, resume.getUploadedAt());
    }

    @Transactional(readOnly = true)
    public Resume download(Long associateId) {
        if (!associateRepository.existsById(associateId)) {
            throw new NotFoundException("Associate", associateId);
        }
        return resumeRepository.findByAssociateId(associateId)
                .orElseThrow(() -> new NotFoundException("Résumé for associate", associateId));
    }

    public void delete(Long associateId) {
        if (!associateRepository.existsById(associateId)) {
            throw new NotFoundException("Associate", associateId);
        }
        if (!resumeRepository.existsByAssociateId(associateId)) {
            throw new NotFoundException("Résumé for associate", associateId);
        }
        resumeRepository.deleteByAssociateId(associateId);
        auditService.record("DELETED", "Associate", associateId, "Deleted résumé");
    }

    @Transactional(readOnly = true)
    public Optional<ResumeMetaResponse> metaFor(Long associateId) {
        return resumeRepository.findMetaByAssociateId(associateId)
                .map(m -> new ResumeMetaResponse(m.getFilename(), m.getContentType(), m.getByteSize(), m.getUploadedAt()));
    }

    private void validateFileType(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }
        String contentType = file.getContentType();
        String filename = file.getOriginalFilename();
        if (contentType == null) {
            contentType = "";
        }
        if (filename == null) {
            filename = "";
        }

        String lowerContentType = contentType.toLowerCase();
        String lowerFilename = filename.toLowerCase();

        boolean isPdf = lowerContentType.equals("application/pdf") || lowerFilename.endsWith(".pdf");
        boolean isDocx = lowerContentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") || lowerFilename.endsWith(".docx");

        if (!isPdf && !isDocx) {
            throw new BadRequestException("Unsupported file type. Only PDF (.pdf) and Word (.docx) documents are allowed.");
        }
    }
}
