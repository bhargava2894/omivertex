package com.softility.omivertex.service;

import com.softility.omivertex.domain.Proficiency;

import java.util.List;

/**
 * Generates an assistant reply from a workforce context, prior turns, and the
 * user's question. Abstracted so the endpoint depends on the contract, not the
 * Gemini SDK/REST shape — and so tests supply a stub instead of calling Google.
 */
public interface GeminiClient {

    String reply(String workforceContext, List<Turn> history, String userMessage);

    /** True when an API key is present; callers use this to pick AI vs fallback paths. */
    boolean isConfigured();

    /**
     * Structured skill extraction from resume text. Matches only against the
     * supplied taxonomy; throws on any upstream/parse failure (callers fall back).
     */
    ResumeExtraction extractResume(String resumeText, List<SkillOption> taxonomy);

    /** One prior chat turn; role is "user" or "model". */
    record Turn(String role, String content) {}

    /** One taxonomy entry offered to the model for matching. */
    record SkillOption(Long skillId, String name) {}

    /** One skill the model found, with its estimated level and supporting quote. */
    record ExtractedSkill(Long skillId, Proficiency proficiency, String evidence) {}

    /** Full extraction result. */
    record ResumeExtraction(List<ExtractedSkill> skills, String experienceSummary) {}
}
