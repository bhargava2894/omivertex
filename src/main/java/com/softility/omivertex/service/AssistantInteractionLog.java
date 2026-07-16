package com.softility.omivertex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One log line per assistant turn — the only record that Mirai was used.
 * Log-file only by design (privacy decision 2026-07-16): no DB row, and the
 * reply text is never written anywhere — the line says a question was
 * answered, not what the answer said. Failures here are swallowed; a logging
 * bug must never take the assistant down.
 */
@Component
public class AssistantInteractionLog {

    private static final Logger log = LoggerFactory.getLogger(AssistantInteractionLog.class);

    /** How the turn ended: prose reply, a draft to confirm, or an exception. */
    public enum Outcome { ANSWERED, DRAFTED, ERROR }

    public void record(String user, Outcome outcome, List<String> tools, long latencyMs,
                       String question) {
        try {
            String safeQuestion = question == null ? "" : question.replace("\"", "\\\"");
            log.info("MIRAI user={} outcome={} tools={} latencyMs={} question=\"{}\"",
                    user, outcome, tools, latencyMs, safeQuestion);
        } catch (RuntimeException e) {
            // deliberately swallowed — see class javadoc
        }
    }
}
