package com.gis.warplan.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.Functionality;
import com.gis.warplan.domain.RoleCodes;
import com.gis.warplan.domain.RoleEntity;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.FunctionalityRepository;
import com.gis.warplan.repository.RoleRepository;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.NotFoundException;

@Service
public class RoleService {

    private static final Logger log = LogManager.getLogger(RoleService.class);

    private static final Set<String> BUILT_IN = Set.of(RoleCodes.ADMINISTRATOR, RoleCodes.SIDE_ADMINISTRATOR,
            RoleCodes.COMMANDER, RoleCodes.PLANNER);

    private final RoleRepository roles;
    private final FunctionalityRepository functionalities;
    private final AppUserRepository users;
    private final AuditService audit;

    public RoleService(RoleRepository roles, FunctionalityRepository functionalities, AppUserRepository users, AuditService audit) {
        this.roles = roles;
        this.functionalities = functionalities;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Functionality> listFunctionalities() {
        return functionalities.findAll(Sort.by(Sort.Order.asc("name").ignoreCase()));
    }

    @Transactional(readOnly = true)
    public List<RoleEntity> list() {
        return roles.findAll(Sort.by(Sort.Order.asc("name").ignoreCase()));
    }

    @Transactional(readOnly = true)
    public RoleEntity get(Long id) {
        return roles.findById(id).orElseThrow(() -> new NotFoundException("Role not found."));
    }

    @Transactional
    public RoleEntity create(String name, String description, List<Long> functionalityIds) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new BadRequestException("Role name is required.");
        }
        RoleEntity role = new RoleEntity();
        role.setCode(uniqueSystemKey(trimmed));
        role.setName(trimmed);
        role.setDescription(description);
        role.setFunctionalityIds(checkedFunctionalities(functionalityIds));
        role.setStandard(false);
        RoleEntity saved = roles.save(role);
        log.info("role.created id={} name={}", saved.getId(), saved.getName());
        audit.record("CREATE", "Role", saved.getId(), "Role " + saved.getName() + " created",
                Map.of("functionalityIds", saved.getFunctionalityIds()));
        return saved;
    }

    @Transactional
    public RoleEntity update(Long id, String name, String description, List<Long> functionalityIds) {
        RoleEntity role = get(id);
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isBlank()) {
            throw new BadRequestException("Role name is required.");
        }
        List<Long> checked = checkedFunctionalities(functionalityIds);
        if (RoleCodes.ADMINISTRATOR.equals(role.getCode())) {
            throw new ConflictException("The administrator role always has every functionality and cannot be edited.");
        }
        role.setName(trimmed);
        role.setDescription(description);
        role.setFunctionalityIds(checked);
        RoleEntity saved = roles.save(role);
        log.info("role.updated id={} name={}", saved.getId(), saved.getName());
        audit.record("UPDATE", "Role", saved.getId(), "Role " + saved.getName() + " updated",
                Map.of("functionalityIds", saved.getFunctionalityIds()));
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        RoleEntity role = get(id);
        if (BUILT_IN.contains(role.getCode()) || role.isStandard()) {
            throw new ConflictException("Built-in roles cannot be deleted.");
        }
        for (AppUser user : users.findAll()) {
            if (user.getRoleIds() != null && user.getRoleIds().contains(id)) {
                throw new ConflictException("The role is assigned to users.");
            }
        }
        roles.delete(role);
        log.info("role.deleted id={} name={}", id, role.getName());
        audit.record("DELETE", "Role", id, "Role " + role.getName() + " deleted", Map.of());
    }

    /** Internal unique key for DB/auth — never exposed on the role API. */
    private String uniqueSystemKey(String name) {
        String base = name.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isBlank()) {
            base = "ROLE";
        }
        if (!Character.isLetter(base.charAt(0))) {
            base = "ROLE_" + base;
        }
        if (base.length() > 50) {
            base = base.substring(0, 50).replaceAll("_+$", "");
        }
        String candidate = base;
        int n = 2;
        while (roles.findByCode(candidate).isPresent()) {
            String suffix = "_" + n++;
            candidate = base.substring(0, Math.min(base.length(), 60 - suffix.length())) + suffix;
        }
        return candidate;
    }

    private List<Long> checkedFunctionalities(List<Long> functionalityIds) {
        Set<Long> unique = new LinkedHashSet<>(functionalityIds == null ? List.of() : functionalityIds);
        if (unique.contains(null)) {
            throw new BadRequestException("functionalityIds cannot contain null.");
        }
        if (functionalities.findAllById(unique).size() != unique.size()) {
            throw new BadRequestException("One or more functionalityIds do not exist.");
        }
        return new ArrayList<>(unique);
    }
}
