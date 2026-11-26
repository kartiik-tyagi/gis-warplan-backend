package com.gis.warplan.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.Side;
import com.gis.warplan.domain.enums.EnumCatalog;
import com.gis.warplan.domain.enums.EnumCatalog.ListDef;
import com.gis.warplan.domain.enums.EnumCatalog.Seed;
import com.gis.warplan.domain.enums.EnumMutable;
import com.gis.warplan.domain.enums.EnumSideKey;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.web.ApiException;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.ConflictException;
import com.gis.warplan.web.NotFoundException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

@Service
public class EnumCatalogService {

    private static final Logger log = LogManager.getLogger(EnumCatalogService.class);
    private static final String CODE_PATTERN = "^[A-Z][A-Z0-9_]{0,39}$";

    private final EntityManager em;
    private final SideRepository sides;

    public EnumCatalogService(EntityManager em, SideRepository sides) {
        this.em = em;
        this.sides = sides;
    }

    public List<ListDef> lists() {
        return EnumCatalog.lists();
    }

    public ListDef requireList(String key) {
        return EnumCatalog.find(key).orElseThrow(() -> new NotFoundException("The list was not found."));
    }

    @Transactional(readOnly = true)
    public List<EnumMutable> list(ListDef list) {
        return rows(list.type());
    }

    @Transactional(readOnly = true)
    public List<EnumMutable> choices(ListDef list, Long sideId) {
        Long requiredSide = sideForRead(list, sideId);
        List<EnumMutable> result = new ArrayList<>();
        for (EnumMutable row : rows(list.type())) {
            if (!row.isActive()) {
                continue;
            }
            if (list.sideSpecific() && !requiredSide.equals(row.getSideId())) {
                continue;
            }
            result.add(row);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public EnumMutable get(ListDef list, String code, Long sideId) {
        Long requiredSide = sideForRead(list, sideId);
        EnumMutable row = find(list, normalizeCode(code), requiredSide);
        if (row == null) {
            throw new NotFoundException("The choice was not found.");
        }
        return row;
    }

    @Transactional
    public EnumMutable create(ListDef list, String code, String label, Integer sortOrder, Boolean active, Long sideId) {
        String storedCode = requireCode(code);
        String storedLabel = requireLabel(label);
        int storedOrder = requireOrder(sortOrder);
        boolean storedActive = requireActive(active);
        Long storedSide = sideForWrite(list, sideId);
        if (find(list, storedCode, storedSide) != null) {
            throw new ConflictException("A choice with this code already exists.");
        }
        ensureLabelFree(list, storedLabel, storedCode, storedSide);
        EnumMutable created = blank(list);
        created.setCode(storedCode);
        if (list.sideSpecific()) {
            created.setSideId(storedSide);
        }
        created.setLabel(storedLabel);
        created.setSortOrder(storedOrder);
        created.setActive(storedActive);
        em.persist(created);
        log.info("enum.created list={} code={}", list.key(), storedCode);
        return created;
    }

    /**
     * {@code locatedSide} is the side the row was opened with. {@code sideId} is the side to store.
     * They differ only when a side-specific choice moves to another country side.
     */
    @Transactional
    public EnumMutable update(ListDef list, String pathCode, Long locatedSide, String code, String label, Integer sortOrder,
            Boolean active, Long sideId) {
        String storedCode = requireCode(pathCode);
        String bodyCode = requireCode(code);
        if (!storedCode.equals(bodyCode)) {
            throw new ApiException("CODE_IMMUTABLE", HttpStatus.BAD_REQUEST, "The code cannot be changed.",
                    Map.of("code", "The code cannot be changed."));
        }
        String storedLabel = requireLabel(label);
        int storedOrder = requireOrder(sortOrder);
        boolean storedActive = requireActive(active);
        Long storedSide = sideForWrite(list, sideId);
        if (list.sideSpecific() && locatedSide == null) {
            throw invalid(Map.of("sideId", "Choose a side."));
        }
        if (!list.sideSpecific() && locatedSide != null) {
            throw invalid(Map.of("sideId", "This list does not use a side."));
        }
        EnumMutable existing = find(list, storedCode, list.sideSpecific() ? locatedSide : null);
        if (existing == null) {
            throw new NotFoundException("The choice was not found.");
        }
        if (list.sideSpecific() && !storedSide.equals(existing.getSideId())) {
            if (find(list, storedCode, storedSide) != null) {
                throw new ConflictException("A choice with this code already exists.");
            }
            ensureLabelFree(list, storedLabel, storedCode, storedSide);
            em.remove(existing);
            em.flush();
            EnumMutable moved = blank(list);
            moved.setCode(storedCode);
            moved.setSideId(storedSide);
            moved.setLabel(storedLabel);
            moved.setSortOrder(storedOrder);
            moved.setActive(storedActive);
            em.persist(moved);
            log.info("enum.updated list={} code={}", list.key(), storedCode);
            return moved;
        }
        ensureLabelFree(list, storedLabel, storedCode, existing.getSideId());
        existing.setLabel(storedLabel);
        existing.setSortOrder(storedOrder);
        existing.setActive(storedActive);
        log.info("enum.updated list={} code={}", list.key(), storedCode);
        return existing;
    }

    @Transactional(readOnly = true)
    public boolean sideInUse(Long sideId) {
        if (sideId == null) {
            return false;
        }
        for (ListDef list : EnumCatalog.lists()) {
            if (!list.sideSpecific()) {
                continue;
            }
            for (EnumMutable row : rows(list.type())) {
                if (sideId.equals(row.getSideId())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public String sideName(Long sideId) {
        if (sideId == null) {
            return null;
        }
        return sides.findById(sideId).map(Side::getName).orElse(null);
    }

    /** Inserts missing seed rows. An existing code is left as the user saved it. */
    @Transactional
    public void seedMissing() {
        for (ListDef list : EnumCatalog.lists()) {
            for (Seed seed : list.seeds()) {
                Long sideId = null;
                if (list.sideSpecific()) {
                    List<Side> matches = countrySidesNamed(seed.sideName());
                    if (matches.size() != 1) {
                        log.warn("enum.seed.skipped list={} code={} reason={}", list.key(), seed.code(),
                                matches.isEmpty() ? "missing_side" : "ambiguous_side");
                        continue;
                    }
                    sideId = matches.get(0).getId();
                }
                if (find(list, seed.code(), sideId) != null) {
                    continue;
                }
                EnumMutable created = blank(list);
                created.setCode(seed.code());
                if (list.sideSpecific()) {
                    created.setSideId(sideId);
                }
                created.setLabel(seed.label());
                created.setSortOrder(seed.sortOrder());
                created.setActive(true);
                em.persist(created);
                log.info("enum.seeded list={} code={}", list.key(), seed.code());
            }
        }
    }

    private List<Side> countrySidesNamed(String name) {
        return sides.findByName(name).stream()
                .filter(side -> SideService.KIND_COUNTRY.equals(side.getKind()))
                .toList();
    }

    private Long sideForRead(ListDef list, Long sideId) {
        if (list.sideSpecific()) {
            if (sideId == null) {
                throw invalid(Map.of("sideId", "Choose a side."));
            }
            return sideId;
        }
        if (sideId != null) {
            throw invalid(Map.of("sideId", "This list does not use a side."));
        }
        return null;
    }

    private Long sideForWrite(ListDef list, Long sideId) {
        if (!list.sideSpecific()) {
            if (sideId != null) {
                throw invalid(Map.of("sideId", "This list does not use a side."));
            }
            return null;
        }
        if (sideId == null) {
            throw invalid(Map.of("sideId", "Choose a side."));
        }
        Side side = sides.findById(sideId).orElse(null);
        if (side == null) {
            throw invalid(Map.of("sideId", "The side was not found."));
        }
        if (!SideService.KIND_COUNTRY.equals(side.getKind())) {
            throw invalid(Map.of("sideId", "Choose a country side."));
        }
        return side.getId();
    }

    private void ensureLabelFree(ListDef list, String label, String code, Long sideId) {
        String wanted = label.toLowerCase(Locale.ROOT);
        for (EnumMutable row : rows(list.type())) {
            if (code.equals(row.getCode()) && Objects.equals(sideId, row.getSideId())) {
                continue;
            }
            if (list.sideSpecific() && !Objects.equals(sideId, row.getSideId())) {
                continue;
            }
            if (wanted.equals(row.getLabel().trim().toLowerCase(Locale.ROOT))) {
                throw new ConflictException("A choice with this label already exists.");
            }
        }
    }

    private EnumMutable find(ListDef list, String code, Long sideId) {
        if (code == null) {
            return null;
        }
        Object id = list.sideSpecific() ? new EnumSideKey(code, sideId) : code;
        return em.find(list.type(), id);
    }

    private String requireCode(String code) {
        String stored = normalizeCode(code);
        if (stored == null || !stored.matches(CODE_PATTERN)) {
            throw invalid(Map.of("code", "Use letters, digits, and underscores, starting with a letter."));
        }
        return stored;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim().toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String requireLabel(String label) {
        if (label == null || label.trim().isEmpty()) {
            throw invalid(Map.of("label", "Enter a label."));
        }
        String stored = label.trim();
        if (stored.length() > 120) {
            throw invalid(Map.of("label", "Use at most 120 characters."));
        }
        for (int i = 0; i < stored.length(); i++) {
            if (Character.isISOControl(stored.charAt(i))) {
                throw invalid(Map.of("label", "Use plain text."));
            }
        }
        return stored;
    }

    private int requireOrder(Integer sortOrder) {
        if (sortOrder == null || sortOrder < 0 || sortOrder > 10000) {
            throw invalid(Map.of("sortOrder", "Enter a number from 0 to 10000."));
        }
        return sortOrder;
    }

    private boolean requireActive(Boolean active) {
        if (active == null) {
            throw invalid(Map.of("active", "Choose whether this choice is available."));
        }
        return active;
    }

    private BadRequestException invalid(Map<String, String> fields) {
        return new BadRequestException("The request is not valid.", new LinkedHashMap<>(fields));
    }

    private EnumMutable blank(ListDef list) {
        try {
            return list.type().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not create a choice for " + list.key(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<EnumMutable> rows(Class<? extends EnumMutable> type) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<EnumMutable> query = (CriteriaQuery<EnumMutable>) cb.createQuery(type);
        Root<EnumMutable> root = (Root<EnumMutable>) query.from(type);
        query.select(root);
        query.orderBy(cb.asc(root.get("sortOrder")), cb.asc(root.get("label")), cb.asc(root.get("code")));
        return em.createQuery(query).getResultList();
    }
}
