package com.gis.warplan.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.Functionality;
import com.gis.warplan.domain.RoleCodes;
import com.gis.warplan.domain.RoleEntity;
import com.gis.warplan.domain.StandardOrbat;
import com.gis.warplan.domain.UserSession;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.FunctionalityRepository;
import com.gis.warplan.repository.RoleRepository;
import com.gis.warplan.repository.StandardOrbatRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.repository.UserSessionRepository;
import com.gis.warplan.security.UserPrincipal;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.ForbiddenException;
import com.gis.warplan.web.NotFoundException;

@Service
public class UserService {

    private final AppUserRepository users;
    private final RoleRepository roles;
    private final FunctionalityRepository functionalities;
    private final StandardOrbatRepository standardOrbats;
    private final SideRepository sides;
    private final UserSessionRepository sessions;
    private final PasswordEncoder passwordEncoder;
    private final ThemeService themes;
    private final AccessService access;
    private final AuditService audit;

    public UserService(AppUserRepository users, RoleRepository roles, FunctionalityRepository functionalities,
            StandardOrbatRepository standardOrbats, SideRepository sides, UserSessionRepository sessions,
            PasswordEncoder passwordEncoder, ThemeService themes, AccessService access, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.functionalities = functionalities;
        this.standardOrbats = standardOrbats;
        this.sides = sides;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.themes = themes;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AppUser> list() {
        access.requireFunctionality(FunctionalityCodes.MANAGE_USERS);
        Visibility visibility = visibility();
        return users.findAll(Sort.by("id")).stream().filter(visibility::sees).toList();
    }

    @Transactional(readOnly = true)
    public AppUser get(Long id) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_USERS);
        AppUser user = find(id);
        if (!visibility().sees(user)) {
            throw new ForbiddenException("You can only manage users of the side you administer.");
        }
        return user;
    }

    @Transactional
    public AppUser patch(Long id, String name, String password, List<Long> roleIds, Boolean active,
            Long administeredSideId, Long themeId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_USERS);
        UserPrincipal caller = access.requirePrincipal();
        AppUser user = find(id);
        if (!visibility().sees(user)) {
            throw new ForbiddenException("You can only manage users of the side you administer.");
        }
        if (!access.isAdministrator() && access.isAdministrator(user)) {
            throw new ForbiddenException("Only an administrator can change an administrator.");
        }
        Map<String, Object> changed = new LinkedHashMap<>();
        boolean revoke = false;
        if (name != null) {
            String trimmed = name.trim();
            if (trimmed.isEmpty()) {
                throw new BadRequestException("Name cannot be blank.");
            }
            users.findByName(trimmed).filter(other -> !other.getId().equals(id)).ifPresent(other -> {
                throw new ConflictException("A user with this name already exists.");
            });
            user.setName(trimmed);
            changed.put("name", trimmed);
        }
        if (password != null) {
            user.setPasswordHash(passwordEncoder.encode(password));
            changed.put("password", "changed");
            revoke = true;
        }
        if (roleIds != null) {
            List<Long> checked = assignableRoles(roleIds);
            boolean keepsAdmin = checked.contains(adminRoleId());
            if (!keepsAdmin && access.isAdministrator(user)) {
                requireAnotherAdministrator(user);
            }
            user.setRoleIds(checked);
            changed.put("roleIds", checked);
            revoke = true;
        }
        if (active != null && active != user.isActive()) {
            if (!active) {
                if (caller.userId().equals(id)) {
                    throw new ConflictException("You cannot deactivate your own account.");
                }
                if (access.isAdministrator(user)) {
                    requireAnotherAdministrator(user);
                }
                revoke = true;
            }
            user.setActive(active);
            changed.put("active", active);
        }
        if (administeredSideId != null) {
            if (!access.isAdministrator()) {
                throw new ForbiddenException("Only an administrator can assign the administered side.");
            }
            if (!sides.existsById(administeredSideId)) {
                throw new BadRequestException("Side " + administeredSideId + " does not exist.");
            }
            user.setAdministeredSideId(administeredSideId);
            changed.put("administeredSideId", administeredSideId);
        }
        if (themeId != null) {
            themes.requireExists(themeId);
            user.setThemeId(themeId);
            changed.put("themeId", themeId);
        }
        AppUser saved = users.save(user);
        if (revoke) {
            revokeSessions(saved.getId());
        }
        audit.record("UPDATE", "AppUser", saved.getId(), "User " + saved.getName() + " updated", changed);
        return saved;
    }

    @Transactional
    public AppUser chooseTheme(Long themeId) {
        access.requireFunctionality(FunctionalityCodes.CHOOSE_THEME);
        themes.requireExists(themeId);
        AppUser user = find(access.requirePrincipal().userId());
        user.setThemeId(themeId);
        AppUser saved = users.save(user);
        audit.record("UPDATE", "AppUser", saved.getId(), "User " + saved.getName() + " chose a theme",
                Map.of("themeId", themeId));
        return saved;
    }

    @Transactional
    public AppUser markFromNode(Long standardOrbatId, String standardNodeId, String name, String password,
            List<Long> roleIds) {
        access.requireFunctionality(FunctionalityCodes.MARK_AS_USER);
        StandardOrbat orbat = standardOrbats.findById(standardOrbatId)
                .orElseThrow(() -> new NotFoundException("Standard ORBAT not found."));
        access.requireSideScope(orbat.getSideId());
        boolean nodeExists = OrbatSupport.nodesOf(orbat.getOrbatJson()).stream()
                .anyMatch(node -> standardNodeId.equals(node.get("id")));
        if (!nodeExists) {
            throw new NotFoundException("The node does not exist in this ORBAT.");
        }
        for (AppUser linked : users.findByStandardNodeIdIsNotNull()) {
            if (standardNodeId.equals(linked.getStandardNodeId())) {
                throw new ConflictException("The node is already marked as user " + linked.getName() + ".");
            }
        }
        String trimmed = name.trim();
        if (users.findByName(trimmed).isPresent()) {
            throw new ConflictException("A user with this name already exists.");
        }
        List<Long> checked = assignableRoles(roleIds);
        AppUser user = new AppUser();
        user.setName(trimmed);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRoleIds(checked);
        user.setActive(true);
        user.setStandardOrbatId(standardOrbatId);
        user.setStandardNodeId(standardNodeId);
        AppUser saved = users.save(user);
        audit.record("MARK_USER", "AppUser", saved.getId(), "Node " + standardNodeId + " marked as user " + trimmed,
                Map.of("standardOrbatId", standardOrbatId, "standardNodeId", standardNodeId, "roleIds", checked));
        return saved;
    }

    private AppUser find(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User not found."));
    }

    /**
     * Roles must exist. Anyone but an administrator is also barred from handing out the administrator role
     * or any functionality they do not hold themselves.
     */
    private List<Long> assignableRoles(List<Long> roleIds) {
        Set<Long> unique = new LinkedHashSet<>(roleIds == null ? List.of() : roleIds);
        if (unique.isEmpty() || unique.contains(null)) {
            throw new BadRequestException("At least one valid role is required.");
        }
        List<RoleEntity> found = roles.findAllById(unique);
        if (found.size() != unique.size()) {
            throw new BadRequestException("One or more roleIds do not exist.");
        }
        if (!access.isAdministrator()) {
            UserPrincipal caller = access.requirePrincipal();
            for (RoleEntity role : found) {
                if (RoleCodes.ADMINISTRATOR.equals(role.getCode())) {
                    throw new ForbiddenException("Only an administrator can assign the administrator role.");
                }
                List<Long> functionalityIds = role.getFunctionalityIds() == null ? List.of() : role.getFunctionalityIds();
                for (Functionality functionality : functionalities.findAllById(functionalityIds)) {
                    if (!caller.hasFunctionality(functionality.getCode())) {
                        throw new ForbiddenException("You cannot assign a role with functionalities you do not hold.");
                    }
                }
            }
        }
        return new ArrayList<>(unique);
    }

    private Long adminRoleId() {
        return roles.findByCode(RoleCodes.ADMINISTRATOR).map(RoleEntity::getId).orElse(-1L);
    }

    private void requireAnotherAdministrator(AppUser leaving) {
        Long adminRole = adminRoleId();
        boolean other = users.findAll().stream()
                .anyMatch(user -> !user.getId().equals(leaving.getId()) && user.isActive()
                        && user.getRoleIds() != null && user.getRoleIds().contains(adminRole));
        if (!other) {
            throw new ConflictException("At least one active administrator must remain.");
        }
    }

    private void revokeSessions(Long userId) {
        Instant now = Instant.now();
        for (UserSession session : sessions.findByUserId(userId)) {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(now);
                sessions.save(session);
            }
        }
    }

    private Visibility visibility() {
        if (!access.isSideScoped()) {
            return user -> true;
        }
        Long own = access.administeredSideId();
        Map<Long, Long> orbatSides = new HashMap<>();
        standardOrbats.findAll().forEach(orbat -> orbatSides.put(orbat.getId(), orbat.getSideId()));
        return user -> own != null && (own.equals(user.getAdministeredSideId())
                || (user.getStandardOrbatId() != null && own.equals(orbatSides.get(user.getStandardOrbatId()))));
    }

    private interface Visibility {
        boolean sees(AppUser user);
    }
}
