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
import com.drdo.jayant.repository.AppUserRepository;
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
    private final AppUserRepository users;

    public AccessService(RoleRepository roles, FeatureRepository features, ThemeRepository themes,
            AppUserRepository users) {
        this.roles = roles;
        this.features = features;
        this.themes = themes;
        this.users = users;
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

    /** Passes when the caller holds at least one of the features, or is an administrator. */
    public void requireAnyFeature(String... codes) {
        UserPrincipal principal = requirePrincipal();
        if (principal.hasRole(RoleCodes.ADMINISTRATOR)) {
            return;
        }
        for (String code : codes) {
            if (principal.hasFeature(code)) {
                return;
            }
        }
        throw new ForbiddenException("You do not have access to this feature.");
    }

    public boolean hasFeature(String code) {
        return currentPrincipal().map(principal -> principal.hasFeature(code)).orElse(false);
    }

    public void requireAdministrator() {
        if (!isAdministrator()) {
            throw new ForbiddenException("Administrator access is required.");
        }
    }

    public boolean isAdministrator() {
        return currentPrincipal().map(principal -> principal.hasRole(RoleCodes.ADMINISTRATOR)).orElse(false);
    }

    /** A team administrator (without the administrator role) only manages the team they administer. */
    public boolean isTeamScoped() {
        return currentPrincipal()
                .map(principal -> !principal.hasRole(RoleCodes.ADMINISTRATOR)
                        && principal.hasRole(RoleCodes.TEAM_ADMINISTRATOR))
                .orElse(false);
    }

    /** The team the caller administers, read fresh from the database. */
    @Transactional(readOnly = true)
    public Long administeredTeamId() {
        UserPrincipal principal = requirePrincipal();
        return users.findById(principal.userId()).map(AppUser::getAdministeredTeamId).orElse(null);
    }

    public boolean canManageTeam(Long teamId) {
        if (!isTeamScoped()) {
            return true;
        }
        Long own = administeredTeamId();
        return own != null && own.equals(teamId);
    }

    public void requireTeamScope(Long teamId) {
        if (!canManageTeam(teamId)) {
            throw new ForbiddenException("You can only manage the team you administer.");
        }
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
