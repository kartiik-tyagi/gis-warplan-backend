package com.drdo.jayant.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.drdo.jayant.domain.AppUser;
import com.drdo.jayant.domain.FeatureEntity;
import com.drdo.jayant.domain.RoleCodes;
import com.drdo.jayant.domain.RoleEntity;
import com.drdo.jayant.domain.Theme;
import com.drdo.jayant.repository.FeatureRepository;
import com.drdo.jayant.repository.RoleRepository;
import com.drdo.jayant.repository.ThemeRepository;
import com.drdo.jayant.security.UserPrincipal;
import com.drdo.jayant.web.ForbiddenException;
import com.drdo.jayant.web.UnauthorizedException;

@Service
public class AccessService {

    private final RoleRepository roles;
    private final FeatureRepository features;
    private final ThemeRepository themes;

    public AccessService(RoleRepository roles, FeatureRepository features, ThemeRepository themes) {
        this.roles = roles;
        this.features = features;
        this.themes = themes;
    }

    public Optional<UserPrincipal> currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public UserPrincipal requirePrincipal() {
        return currentPrincipal().orElseThrow(() -> new UnauthorizedException("Authentication is required."));
    }

    public void requireFeature(String code) {
        UserPrincipal principal = requirePrincipal();
        if (!principal.hasFeature(code)) {
            throw new ForbiddenException("You do not have access to this feature.");
        }
    }

    public void requireAdministrator() {
        if (!isAdministrator()) {
            throw new ForbiddenException("Administrator access is required.");
        }
    }

    public boolean isAdministrator() {
        return currentPrincipal().map(principal -> principal.hasRole(RoleCodes.ADMINISTRATOR)).orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isAdministrator(AppUser user) {
        return resolveRoleCodes(user).contains(RoleCodes.ADMINISTRATOR);
    }

    @Transactional(readOnly = true)
    public List<String> resolveRoleCodes(AppUser user) {
        List<String> codes = new ArrayList<>();
        for (RoleEntity role : rolesOf(user)) {
            codes.add(role.getCode());
        }
        return codes;
    }

    @Transactional(readOnly = true)
    public List<String> resolveFeatureCodes(AppUser user) {
        Set<Long> featureIds = new LinkedHashSet<>();
        for (RoleEntity role : rolesOf(user)) {
            if (role.getFeatureIds() != null) {
                featureIds.addAll(role.getFeatureIds());
            }
        }
        List<String> codes = new ArrayList<>();
        for (FeatureEntity feature : features.findAllById(featureIds)) {
            codes.add(feature.getCode());
        }
        codes.sort(String::compareTo);
        return codes;
    }

    @Transactional(readOnly = true)
    public Long resolveThemeId(AppUser user) {
        if (user.getThemeId() != null && themes.existsById(user.getThemeId())) {
            return user.getThemeId();
        }
        return themes.findFirstByDefaultThemeTrueOrderByIdAsc().map(Theme::getId).orElse(null);
    }

    private List<RoleEntity> rolesOf(AppUser user) {
        if (user.getRoleIds() == null || user.getRoleIds().isEmpty()) {
            return List.of();
        }
        return roles.findAllById(user.getRoleIds());
    }
}
