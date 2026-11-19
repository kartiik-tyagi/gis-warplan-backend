package com.gis.warplan.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.gis.warplan.web.BadRequestException;

/**
 * Turns a node-type properties document into the shape ORBAT already reads:
 * {@code seeded}, {@code root}, optional {@code parents}, and {@code fields}.
 * An empty parent list is omitted, which means any parent. A root with no parents
 * can only sit at the top. A root that names parents may also sit under those types.
 */
public final class NodeTypeRules {

    public static final int MAX_FIELDS = 30;
    public static final int MAX_OPTIONS = 40;
    public static final int MAX_PARENTS = 40;
    public static final int MAX_LABEL = 80;
    public static final int MAX_KEY = 40;
    public static final int MAX_OPTION = 40;

    private static final int MAX_ERRORS = 48;
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]*$");
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Set<String> PROPERTY_KEYS = Set.of("seeded", "root", "parents", "fields");
    private static final Set<String> FIELD_KEYS = Set.of("key", "label", "type", "required", "options");
    private static final Set<String> TYPES = Set.of("text", "number", "choice");
    private static final Set<String> RESERVED = Set.of(
            "id", "parentid", "nodetypeid", "homesideid", "name", "iconpath", "sourcenodeid");

    private NodeTypeRules() {
    }

    public static Map<String, Object> normalize(Map<String, Object> incoming, String ownCode, Set<String> knownCodes) {
        if (incoming == null || incoming.isEmpty()) {
            return defaults();
        }
        Map<String, String> errors = new LinkedHashMap<>();
        for (String key : incoming.keySet()) {
            if (!PROPERTY_KEYS.contains(key)) {
                put(errors, "properties." + key, "This property is not supported.");
            }
        }
        readFlag(incoming.get("seeded"), "properties.seeded", true, errors);
        boolean root = readFlag(incoming.get("root"), "properties.root", false, errors);
        List<String> parents = readParents(incoming.get("parents"), ownCode, knownCodes, errors);
        List<Map<String, Object>> fields = readFields(incoming.get("fields"), errors);
        if (!errors.isEmpty()) {
            throw new BadRequestException("The node type properties are not valid.", errors);
        }
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("seeded", false);
        stored.put("root", root);
        if (!parents.isEmpty()) {
            stored.put("parents", parents);
        }
        stored.put("fields", fields);
        return stored;
    }

    public static Map<String, Object> defaults() {
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("seeded", false);
        stored.put("root", false);
        stored.put("fields", List.of());
        return stored;
    }

    private static boolean readFlag(Object value, String path, boolean rejectTrue, Map<String, String> errors) {
        if (value == null) {
            return false;
        }
        if (!(value instanceof Boolean flag)) {
            put(errors, path, "Must be true or false.");
            return false;
        }
        if (rejectTrue && flag) {
            put(errors, path, "Seeded types come from the catalogue.");
            return false;
        }
        return flag;
    }

    private static List<String> readParents(Object raw, String ownCode, Set<String> knownCodes, Map<String, String> errors) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            put(errors, "properties.parents", "Parents must be a list of node type codes.");
            return List.of();
        }
        if (list.size() > MAX_PARENTS) {
            put(errors, "properties.parents", "Choose at most " + MAX_PARENTS + " parents.");
        }
        String self = ownCode == null ? "" : ownCode.trim().toUpperCase(Locale.ROOT);
        List<String> parents = new ArrayList<>();
        int limit = Math.min(list.size(), MAX_PARENTS);
        for (int index = 0; index < limit; index++) {
            String path = "properties.parents[" + index + "]";
            Object item = list.get(index);
            if (!(item instanceof String text)) {
                put(errors, path, "Each parent must be a node type code.");
                continue;
            }
            String code = text.trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty() || code.length() > 60 || !CODE.matcher(code).matches()) {
                put(errors, path, "Use a letter, then letters, numbers, or underscores.");
                continue;
            }
            if (code.equals(self)) {
                put(errors, path, "A type cannot list itself as a parent.");
                continue;
            }
            if (knownCodes == null || !knownCodes.contains(code)) {
                put(errors, path, "No node type uses this code.");
                continue;
            }
            if (parents.contains(code)) {
                put(errors, path, "This parent is listed more than once.");
                continue;
            }
            parents.add(code);
        }
        return parents;
    }

    private static List<Map<String, Object>> readFields(Object raw, Map<String, String> errors) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            put(errors, "properties.fields", "Attributes must be a list.");
            return List.of();
        }
        if (list.size() > MAX_FIELDS) {
            put(errors, "properties.fields", "A node type can have at most " + MAX_FIELDS + " attributes.");
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        int limit = Math.min(list.size(), MAX_FIELDS);
        for (int index = 0; index < limit; index++) {
            Object item = list.get(index);
            String path = "properties.fields[" + index + "]";
            if (!(item instanceof Map<?, ?> field)) {
                put(errors, path, "Each attribute must be an object.");
                keys.add("");
                labels.add("");
                continue;
            }
            for (Object key : field.keySet()) {
                if (!FIELD_KEYS.contains(String.valueOf(key))) {
                    put(errors, path + "." + key, "This property is not supported.");
                }
            }
            String key = readKey(field.get("key"), path + ".key", errors);
            String label = readLabel(field.get("label"), path + ".label", errors);
            String type = readType(field.get("type"), path + ".type", errors);
            boolean required = readRequired(field.get("required"), path + ".required", errors);
            List<String> options = readOptions(field.get("options"), type, path + ".options", errors);
            keys.add(key);
            labels.add(label.toLowerCase(Locale.ROOT));
            if (key.isEmpty() || label.isEmpty() || type.isEmpty() || errors.containsKey(path + ".required")) {
                continue;
            }
            if ("choice".equals(type) && options.isEmpty()) {
                continue;
            }
            Map<String, Object> stored = new LinkedHashMap<>();
            stored.put("key", key);
            stored.put("label", label);
            stored.put("type", type);
            stored.put("required", required);
            if ("choice".equals(type)) {
                stored.put("options", options);
            }
            fields.add(stored);
        }
        markDuplicates(keys, "key", "Another attribute already uses this key.", errors);
        markDuplicates(labels, "label", "Another attribute already uses this label.", errors);
        if (!errors.isEmpty()) {
            return List.of();
        }
        return fields;
    }

    private static String readKey(Object raw, String path, Map<String, String> errors) {
        if (!(raw instanceof String text) || text.isBlank()) {
            put(errors, path, "Enter a key.");
            return "";
        }
        String key = text.trim().toLowerCase(Locale.ROOT);
        if (key.length() > MAX_KEY) {
            put(errors, path, "Key must be " + MAX_KEY + " characters or fewer.");
            return "";
        }
        if (!KEY.matcher(key).matches()) {
            put(errors, path, "Use a letter, then lowercase letters, numbers, or underscores.");
            return "";
        }
        if (RESERVED.contains(key)) {
            put(errors, path, "This key is reserved.");
            return "";
        }
        return key;
    }

    private static String readLabel(Object raw, String path, Map<String, String> errors) {
        if (!(raw instanceof String text) || text.isBlank()) {
            put(errors, path, "Enter a label.");
            return "";
        }
        String label = text.trim().replaceAll("\\s+", " ");
        if (label.length() > MAX_LABEL) {
            put(errors, path, "Label must be " + MAX_LABEL + " characters or fewer.");
            return "";
        }
        return label;
    }

    private static String readType(Object raw, String path, Map<String, String> errors) {
        if (!(raw instanceof String text) || text.isBlank()) {
            put(errors, path, "Choose text, number, or choice.");
            return "";
        }
        String type = text.trim().toLowerCase(Locale.ROOT);
        if (!TYPES.contains(type)) {
            put(errors, path, "Choose text, number, or choice.");
            return "";
        }
        return type;
    }

    private static boolean readRequired(Object raw, String path, Map<String, String> errors) {
        if (!(raw instanceof Boolean flag)) {
            put(errors, path, "Say whether this attribute is required.");
            return false;
        }
        return flag;
    }

    private static List<String> readOptions(Object raw, String type, String path, Map<String, String> errors) {
        boolean choice = "choice".equals(type);
        if (raw == null) {
            if (choice) {
                put(errors, path, "Add at least one option.");
            }
            return List.of();
        }
        if (!choice) {
            put(errors, path, "Options are only for a choice.");
            return List.of();
        }
        if (!(raw instanceof List<?> list)) {
            put(errors, path, "Options must be a list.");
            return List.of();
        }
        if (list.isEmpty()) {
            put(errors, path, "Add at least one option.");
            return List.of();
        }
        if (list.size() > MAX_OPTIONS) {
            put(errors, path, "An attribute can have at most " + MAX_OPTIONS + " options.");
        }
        List<String> options = new ArrayList<>();
        boolean itemError = false;
        int limit = Math.min(list.size(), MAX_OPTIONS);
        for (int index = 0; index < limit; index++) {
            Object item = list.get(index);
            String itemPath = path + "[" + index + "]";
            if (!(item instanceof String text)) {
                put(errors, itemPath, "Use letters or numbers.");
                itemError = true;
                continue;
            }
            if (text.trim().length() > MAX_LABEL) {
                put(errors, itemPath, "Option is too long.");
                itemError = true;
                continue;
            }
            String option = optionKey(text);
            if (option.isEmpty()) {
                put(errors, itemPath, "Use letters or numbers.");
                itemError = true;
                continue;
            }
            if (option.length() > MAX_OPTION) {
                put(errors, itemPath, "Option is too long.");
                itemError = true;
                continue;
            }
            if (options.contains(option)) {
                put(errors, itemPath, "This option is listed more than once.");
                itemError = true;
                continue;
            }
            options.add(option);
        }
        if (options.isEmpty() && !itemError) {
            put(errors, path, "Add at least one option.");
        }
        return options;
    }

    static String optionKey(String raw) {
        String slug = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return slug.replaceAll("^_+|_+$", "");
    }

    private static void markDuplicates(List<String> values, String field, String message, Map<String, String> errors) {
        for (int index = 0; index < values.size(); index++) {
            String value = values.get(index);
            if (value.isEmpty()) {
                continue;
            }
            int first = values.indexOf(value);
            if (first != index) {
                put(errors, "properties.fields[" + first + "]." + field, message);
                put(errors, "properties.fields[" + index + "]." + field, message);
            }
        }
    }

    private static void put(Map<String, String> errors, String key, String message) {
        if (errors.size() >= MAX_ERRORS && !errors.containsKey(key)) {
            return;
        }
        errors.putIfAbsent(key, message);
    }
}
