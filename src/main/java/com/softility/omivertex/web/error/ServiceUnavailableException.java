package com.softility.omivertex.web.error;

/** 503 — a bounded resource (e.g. the AI executor) is saturated; retry shortly. */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
