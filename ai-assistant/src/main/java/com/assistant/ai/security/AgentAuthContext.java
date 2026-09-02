package com.assistant.ai.security;

import java.util.Set;

/**
 * 一次Agent执行中的可信身份
 *
 */
public record AgentAuthContext(String userId, Set<String> roles, String accessToken) {
    public boolean hasRole(String role) {
        return roles.contains("ROLE_" + role);
    }
}