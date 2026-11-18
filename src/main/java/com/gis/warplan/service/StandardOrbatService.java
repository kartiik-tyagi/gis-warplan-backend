package com.gis.warplan.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.FunctionalityCodes;
import com.gis.warplan.domain.StandardOrbat;
import com.gis.warplan.domain.Side;
import com.gis.warplan.repository.StandardOrbatRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.NotFoundException;

@Service
public class StandardOrbatService {

    private final StandardOrbatRepository orbats;
    private final SideRepository sides;
    private final OrbatSupport support;
    private final AccessService access;
    private final AuditService audit;

    public StandardOrbatService(StandardOrbatRepository orbats, SideRepository sides, OrbatSupport support,
            AccessService access, AuditService audit) {
        this.orbats = orbats;
        this.sides = sides;
        this.support = support;
        this.access = access;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public StandardOrbat get(Long sideId) {
        access.requireAnyFunctionality(FunctionalityCodes.MANAGE_STANDARD_ORBAT, FunctionalityCodes.MARK_AS_USER,
                FunctionalityCodes.MANAGE_PLAYING_ORBAT, FunctionalityCodes.MANAGE_ALLIANCE, FunctionalityCodes.MANAGE_USERS);
        side(sideId);
        access.requireSideScope(sideId);
        return find(sideId);
    }

    @Transactional
    public Created create(Long sideId) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_STANDARD_ORBAT);
        Side side = side(sideId);
        access.requireSideScope(sideId);
        List<StandardOrbat> existing = orbats.findBySideId(sideId);
        if (!existing.isEmpty()) {
            return new Created(existing.get(0), false);
        }
        if (!SideService.KIND_COUNTRY.equals(side.getKind())) {
            throw new BadRequestException("Only country sides have a standard ORBAT.");
        }
        StandardOrbat orbat = new StandardOrbat();
        orbat.setName(side.getName());
        orbat.setSideId(sideId);
        orbat.setOrbatJson(OrbatSupport.document(List.of(OrbatSupport.rootNode(side.getName(), sideId))));
        orbat.setMetadata(new java.util.LinkedHashMap<>());
        StandardOrbat saved = orbats.saveAndFlush(orbat);
        audit.record("CREATE", "StandardOrbat", saved.getId(), "Standard ORBAT created for " + side.getName(),
                Map.of("sideId", sideId));
        return new Created(saved, true);
    }

    @Transactional
    public StandardOrbat update(Long sideId, Map<String, Object> orbatJson, Map<String, Object> metadata, Long version) {
        access.requireFunctionality(FunctionalityCodes.MANAGE_STANDARD_ORBAT);
        side(sideId);
        access.requireSideScope(sideId);
        StandardOrbat orbat = find(sideId);
        if (!Objects.equals(orbat.getVersion(), version)) {
            throw new ConflictException("The standard ORBAT was changed by someone else. Reload and try again.");
        }
        int nodes = support.validate(orbatJson).size();
        orbat.setOrbatJson(orbatJson);
        if (metadata != null) {
            orbat.setMetadata(metadata);
        }
        StandardOrbat saved = orbats.saveAndFlush(orbat);
        audit.record("UPDATE", "StandardOrbat", saved.getId(), "Standard ORBAT saved for side " + sideId,
                Map.of("sideId", sideId, "nodes", nodes, "version", saved.getVersion()));
        return saved;
    }

    private Side side(Long sideId) {
        return sides.findById(sideId).orElseThrow(() -> new NotFoundException("Side not found."));
    }

    private StandardOrbat find(Long sideId) {
        List<StandardOrbat> found = orbats.findBySideId(sideId);
        if (found.isEmpty()) {
            throw new NotFoundException("The side has no standard ORBAT yet.");
        }
        return found.get(0);
    }

    public record Created(StandardOrbat orbat, boolean created) {
    }
}
