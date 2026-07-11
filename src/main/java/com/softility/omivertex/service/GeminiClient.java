package com.softility.omivertex.service;

import com.softility.omivertex.domain.Proficiency;

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
     */
    AssistantReply replyWithTools(String workforceContext, List<Turn> history,
                                  String userMessage, ToolExecutor tools);

    /** True when an API key is present; callers use this to pick AI vs fallback paths. */
    boolean isConfigured();

    /**
     * Structured skill extraction from resume text. Matches only against the
     * supplied taxonomy; throws on any upstream/parse failure (callers fall back).
     */
    ResumeExtraction extractResume(String resumeText, List<SkillOption> taxonomy);

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

    /** Full extraction result. */
    record ResumeExtraction(List<ExtractedSkill> skills, String experienceSummary) {}
}
