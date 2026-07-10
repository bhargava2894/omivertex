package com.softility.omivertex.service;

import java.util.List;

/**
 * Generates an assistant reply from a workforce context, prior turns, and the
 * user's question. Abstracted so the endpoint depends on the contract, not the
 * Gemini SDK/REST shape — and so tests supply a stub instead of calling Google.
 */
public interface GeminiClient {

    String reply(String workforceContext, List<Turn> history, String userMessage);

    /** One prior chat turn; role is "user" or "model". */
    record Turn(String role, String content) {}
}
