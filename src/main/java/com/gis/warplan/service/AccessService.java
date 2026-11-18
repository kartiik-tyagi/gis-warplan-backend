package com.gis.warplan.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.Functionality;
import com.gis.warplan.domain.RoleCodes;
import com.gis.warplan.domain.RoleEntity;
import com.gis.warplan.domain.Theme;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.FunctionalityRepository;
import com.gis.warplan.repository.RoleRepository;
import com.gis.warplan.repository.ThemeRepository;
import com.gis.warplan.security.UserPrincipal;
import com.gis.warplan.web.ForbiddenException;
import com.gis.warplan.web.UnauthorizedException;

@Service
public class AccessService {

    private final RoleRepository roles;
    private final FunctionalityRepository functionalities;
    private final ThemeRepository themes;
    private final AppUserRepository users;

    public AccessService(RoleRepository roles, FunctionalityRepository functionalities, ThemeRepository themes,
            AppUserRepository users) {
        this.roles = roles;
        this.functionalities = functionalities;
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

    public void requireFunctionality(String code) {
        UserPrincipal principal = requirePrincipal();
        if (!principal.hasFunctionality(code)) {
            throw new ForbiddenException("You do not have access to this functionality.");
        }
    }

    /** Passes when the caller holds at least one of the functionalities, or is an administrator. */
    public void requireAnyFunctionality(String... codes) {
        UserPrincipal principal = requirePrincipal();
        if (principal.hasRole(RoleCodes.ADMINISTRATOR)) {
            return;
        }
        for (String code : codes) {
            if (principal.hasFunctionality(code)) {
                return;
            }
        }
        throw new ForbiddenException("You do not have access to this functionality.");
    }

    public boolean hasFunctionality(String code) {
        return currentPrincipal().map(principal -> principal.hasFunctionality(code)).orElse(false);
    }

    public void requireAdministrator() {
        if (!isAdministrator()) {
            throw new ForbiddenException("Administrator access is required.");
        }
    }

    public boolean isAdministrator() {
        return currentPrincipal().map(principal -> principal.hasRole(RoleCodes.ADMINISTRATOR)).orElse(false);
    }

    /** A side administrator (without the administrator role) only manages the side they administer. */
    public boolean isSideScoped() {
        return currentPrincipal()
                .map(principal -> !principal.hasRole(RoleCodes.ADMINISTRATOR)
                        && principal.hasRole(RoleCodes.SIDE_ADMINISTRATOR))
                .orElse(false);
    }

    /** The side the caller administers, read fresh from the database. */
    @Transactional(readOnly = true)
    public Long administeredSideId() {
        UserPrincipal principal = requirePrincipal();
        return users.findById(principal.userId()).map(AppUser::getAdministeredSideId).orElse(null);
    }

    public boolean canManageSide(Long sideId) {
        if (!isSideScoped()) {
            return true;
        }
        Long own = administeredSideId();
        return own != null && own.equals(sideId);
    }

    public void requireSideScope(Long sideId) {
        if (!canManageSide(sideId)) {
            throw new ForbiddenException("You can only manage the side you administer.");
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
    public List<String> resolveFunctionalityCodes(AppUser user) {
        Set<Long> functionalityIds = new LinkedHashSet<>();
        for (RoleEntity role : rolesOf(user)) {
            if (role.getFunctionalityIds() != null) {
                functionalityIds.addAll(role.getFunctionalityIds());
            }
        }
        List<String> codes = new ArrayList<>();
        for (Functionality functionality : functionalities.findAllById(functionalityIds)) {
            codes.add(functionality.getCode());
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
