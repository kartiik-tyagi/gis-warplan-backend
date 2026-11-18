package com.drdo.jayant.security;

import java.util.List;

public record UserPrincipal(
        Long userId,
        String name,
        List<String> roleCodes,
        List<String> functionalityCodes,
        Long administeredSideId,
        String tokenId) {

    public UserPrincipal {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        functionalityCodes = functionalityCodes == null ? List.of() : List.copyOf(functionalityCodes);
    }

    public UserPrincipal(Long userId, String name, List<String> roleCodes, List<String> functionalityCodes,
            Long administeredSideId) {
        this(userId, name, roleCodes, functionalityCodes, administeredSideId, null);
    }

    public boolean hasRole(String code) {
        return roleCodes.contains(code);
    }

    public boolean hasFunctionality(String code) {
        return functionalityCodes.contains(code);
    }
}
