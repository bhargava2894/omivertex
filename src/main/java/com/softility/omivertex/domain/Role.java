package com.softility.omivertex.domain;

/**
 * Access level of an application user. Mirrors the Spring Security roles
 * (ROLE_ADMIN / ROLE_VIEWER / ROLE_ASSOCIATE). ASSOCIATE is a roster-linked
 * self-service login: own profile + change proposals only.
 */
public enum Role {
    ADMIN, VIEWER, ASSOCIATE
}
