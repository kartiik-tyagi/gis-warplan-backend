package com.drdo.jayant.security;

import java.util.List;

public record UserPrincipal(
        Long userId,
        String name,
        List<String> roleCodes,
        List<String> featureCodes,
        Long administeredTeamId,
        String tokenId) {

    public UserPrincipal {
        roleCodes = roleCodes == null ? List.of() : List.copyOf(roleCodes);
        featureCodes = featureCodes == null ? List.of() : List.copyOf(featureCodes);
    }

    public UserPrincipal(Long userId, String name, List<String> roleCodes, List<String> featureCodes,
            Long administeredTeamId) {
        this(userId, name, roleCodes, featureCodes, administeredTeamId, null);
    }

    public boolean hasRole(String code) {
        return roleCodes.contains(code);
    }

    public boolean hasFeature(String code) {
        return featureCodes.contains(code);
    }
}
