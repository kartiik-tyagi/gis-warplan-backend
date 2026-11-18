package com.gis.warplan.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.gis.warplan.repository.NodeTypeRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.web.BadRequestException;

/**
 * Structure rules for {@code orbatJson}: an object with a {@code nodes} array whose items carry
 * id, parentId, nodeTypeId, homeSideId and name (plus any extra fields, which are kept as sent).
 */
@Component
public class OrbatSupport {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_FINALIZED = "finalized";

    private static final int MAX_NODES = 50_000;
    private static final int MAX_ID = 120;
    private static final int MAX_NAME = 200;

    private final NodeTypeRepository nodeTypes;
    private final SideRepository sides;

    public OrbatSupport(NodeTypeRepository nodeTypes, SideRepository sides) {
        this.nodeTypes = nodeTypes;
        this.sides = sides;
    }

    /** Validates the document and returns its nodes. */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> validate(Map<String, Object> orbatJson) {
        if (orbatJson == null) {
            throw new BadRequestException("orbatJson is required.");
        }
        Object raw = orbatJson.get("nodes");
        if (!(raw instanceof List<?> list)) {
            throw new BadRequestException("orbatJson.nodes must be an array.");
        }
        if (list.size() > MAX_NODES) {
            throw new BadRequestException("orbatJson.nodes has too many entries.");
        }
        Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
        Set<Long> typeIds = new HashSet<>();
        Set<Long> sideIds = new HashSet<>();
        for (int index = 0; index < list.size(); index++) {
            if (!(list.get(index) instanceof Map<?, ?> item)) {
                throw new BadRequestException("Node " + index + " must be an object.");
            }
            Map<String, Object> node = (Map<String, Object>) item;
            String id = requiredText(node.get("id"), "Node " + index + " id", MAX_ID);
            requiredText(node.get("name"), "Node " + id + " name", MAX_NAME);
            if (byId.put(id, node) != null) {
                throw new BadRequestException("Duplicate node id " + id + ".");
            }
            Object parent = node.get("parentId");
            if (parent != null && (!(parent instanceof String text) || text.isBlank() || text.equals(id))) {
                throw new BadRequestException("Node " + id + " has an invalid parentId.");
            }
            if (node.get("nodeTypeId") != null) {
                Long typeId = asLong(node.get("nodeTypeId"));
                if (typeId == null) {
                    throw new BadRequestException("Node " + id + " has an invalid nodeTypeId.");
                }
                typeIds.add(typeId);
            }
            Long homeSide = asLong(node.get("homeSideId"));
            if (homeSide == null) {
                throw new BadRequestException("Node " + id + " needs a numeric homeSideId.");
            }
            sideIds.add(homeSide);
        }
        for (Map.Entry<String, Map<String, Object>> entry : byId.entrySet()) {
            Object parent = entry.getValue().get("parentId");
            if (parent != null && !byId.containsKey(parent)) {
                throw new BadRequestException("Node " + entry.getKey() + " refers to unknown parent " + parent + ".");
            }
        }
        rejectCycles(byId);
        if (!typeIds.isEmpty() && nodeTypes.findAllById(typeIds).size() != typeIds.size()) {
            throw new BadRequestException("One or more nodeTypeId values do not exist.");
        }
        if (sides.findAllById(sideIds).size() != sideIds.size()) {
            throw new BadRequestException("One or more homeSideId values do not exist.");
        }
        return new ArrayList<>(byId.values());
    }

    /** Lenient read of already stored nodes. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> nodesOf(Map<String, Object> orbatJson) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        if (orbatJson != null && orbatJson.get("nodes") instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    nodes.add((Map<String, Object>) map);
                }
            }
        }
        return nodes;
    }

    public static Map<String, Object> document(List<Map<String, Object>> nodes) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("nodes", new ArrayList<>(nodes));
        return document;
    }

    public static Map<String, Object> rootNode(String name, Long homeSideId) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", UUID.randomUUID().toString());
        node.put("parentId", null);
        node.put("nodeTypeId", null);
        node.put("homeSideId", homeSideId);
        node.put("name", name);
        return node;
    }

    /**
     * Copies nodes under fresh ids: every copy keeps its fields (including homeSideId), gains
     * sourceNodeId = original id, and has its parentId remapped. Roots are attached to {@code newRootParent}.
     */
    public static List<Map<String, Object>> replicate(List<Map<String, Object>> source, String newRootParent) {
        Map<String, String> ids = new HashMap<>();
        for (Map<String, Object> node : source) {
            ids.put(String.valueOf(node.get("id")), UUID.randomUUID().toString());
        }
        List<Map<String, Object>> copies = new ArrayList<>();
        for (Map<String, Object> node : source) {
            Map<String, Object> copy = new LinkedHashMap<>(node);
            String original = String.valueOf(node.get("id"));
            copy.put("id", ids.get(original));
            copy.put("sourceNodeId", original);
            Object parent = node.get("parentId");
            copy.put("parentId", parent == null ? newRootParent : ids.get(String.valueOf(parent)));
            copies.add(copy);
        }
        return copies;
    }

    public static Long asLong(Object value) {
        if (value instanceof Number number && number.doubleValue() == number.longValue()) {
            return number.longValue();
        }
        return null;
    }

    private static String requiredText(Object value, String label, int max) {
        if (!(value instanceof String text) || text.isBlank()) {
            throw new BadRequestException(label + " is required.");
        }
        if (text.length() > max) {
            throw new BadRequestException(label + " is too long.");
        }
        return text;
    }

    private static void rejectCycles(Map<String, Map<String, Object>> byId) {
        Set<String> safe = new HashSet<>();
        for (String start : byId.keySet()) {
            Set<String> path = new HashSet<>();
            String current = start;
            while (current != null && !safe.contains(current)) {
                if (!path.add(current)) {
                    throw new BadRequestException("The hierarchy contains a cycle at node " + current + ".");
                }
                Object parent = byId.get(current).get("parentId");
                current = parent == null ? null : String.valueOf(parent);
            }
            safe.addAll(path);
        }
    }
}
