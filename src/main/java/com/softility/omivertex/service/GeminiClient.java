package com.softility.omivertex.service;

import com.softility.omivertex.domain.Proficiency;
import com.softility.omivertex.domain.WorkMode;

import java.util.List;

/**
 * Generates an assistant reply from a workforce context, prior turns, and the
 * user's question. Abstracted so the endpoint depends on the contract, not the
 * Gemini SDK/REST shape — and so tests supply a stub instead of calling Google.
 */
public interface GeminiClient {

    /**
     * One assistant turn with tool support. Read tools run via {@code tools};
     * a write tool surfaces as {@link AssistantReply#action()} for the caller
     * to turn into a user-confirmable draft. Never mutates anything itself.
     * {@code adminTools} controls whether admin-only tools are declared to the
     * model at all — a non-admin's model never sees them.
     */
    AssistantReply replyWithTools(String workforceContext, List<Turn> history,
                                  String userMessage, ToolExecutor tools, boolean adminTools);

    /** True when an API key is present; callers use this to pick AI vs fallback paths. */
    boolean isConfigured();

    /**
     * Structured skill extraction from resume text, plus best-effort profile
     * fields (name, phone, employment history). Matches skills only against
     * the supplied taxonomy; throws on any upstream/parse failure (callers
     * fall back).
     */
    ResumeExtraction extractResume(String resumeText, List<SkillOption> taxonomy);

    /**
     * Structured extraction from a job description: a role title, skills matched
     * against the supplied taxonomy, skill names NOT in the taxonomy (raw, never
     * dropped), a cleaned description, work mode / allocation / dates when stated,
     * and the project/client name read from the JD. Throws on upstream/parse
     * failure (callers fall back).
     */
    JobDescriptionExtraction extractJobDescription(
            String jdText, List<SkillOption> taxonomy, List<ProjectOption> projects);

    /** One prior chat turn; role is "user" or "model". */
    record Turn(String role, String content) {}

    /** Executes a read-only tool server-side; returns a compact result for the model. */
    interface ToolExecutor {
        String execute(String name, java.util.Map<String, Object> args);
    }

    /** A write tool the model wants to run — name + raw args, pending resolution. */
    record ActionCall(String name, java.util.Map<String, Object> args) {}

    /** Model output for one turn: prose text and/or a proposed write action. */
    record AssistantReply(String text, ActionCall action) {}

    /** One taxonomy entry offered to the model for matching. */
    record SkillOption(Long skillId, String name) {}

    /** One skill the model found, with its estimated level and supporting quote. */
    record ExtractedSkill(Long skillId, Proficiency proficiency, String evidence) {}

    /** One previous employer extracted from the résumé; dates may be null ("Present"/unclear). */
    record Employment(String company, String title, java.time.LocalDate startDate,
                      java.time.LocalDate endDate) {}

    /** Full extraction result. Profile fields are null / empty when the résumé doesn't state them. */
    record ResumeExtraction(List<ExtractedSkill> skills, String experienceSummary,
                            String name, String phone, List<Employment> employment) {}

    /** One existing project offered to the model for name alignment. */
    record ProjectOption(Long id, String label) {}

    /** Full JD extraction. Any field is null when the JD does not state it. */
    record JobDescriptionExtraction(String title, List<ExtractedSkill> skills,
                                    List<String> unmatchedSkills, String jobDescriptionText,
                                    WorkMode workMode, Integer allocationPercent,
                                    java.time.LocalDate startDate, java.time.LocalDate endDate,
                                    String suggestedProjectName) {}
}
