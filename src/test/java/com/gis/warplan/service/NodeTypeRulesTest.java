package com.gis.warplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gis.warplan.web.BadRequestException;

class NodeTypeRulesTest {

    private static final Set<String> KNOWN = Set.of("BRIGADE", "BATTALION");

    @Test
    void emptyDocumentBecomesACustomTypeWithNoRules() {
        Map<String, Object> stored = NodeTypeRules.normalize(null, "UNIT", KNOWN);
        assertThat(stored.get("seeded")).isEqualTo(false);
        assertThat(stored.get("root")).isEqualTo(false);
        assertThat(stored).doesNotContainKey("parents");
        assertThat((List<?>) stored.get("fields")).isEmpty();
        assertThat(NodeTypeRules.normalize(Map.of(), "UNIT", KNOWN)).isEqualTo(stored);
    }

    @Test
    void keepsRootParentsAndNormalizesChoiceOptions() {
        Map<String, Object> incoming = new LinkedHashMap<>();
        incoming.put("root", true);
        incoming.put("parents", List.of(" brigade ", "BATTALION"));
        incoming.put("fields", List.of(
                field("Role", "  Role   name ", "Choice", true, List.of("Air Defence", "assault")),
                field("strength", "Strength", "number", false, null)));

        Map<String, Object> stored = NodeTypeRules.normalize(incoming, "UNIT", KNOWN);

        assertThat(stored.get("root")).isEqualTo(true);
        assertThat(stored.get("seeded")).isEqualTo(false);
        assertThat(stored.get("parents")).isEqualTo(List.of("BRIGADE", "BATTALION"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) stored.get("fields");
        assertThat(fields).hasSize(2);
        assertThat(fields.get(0).get("key")).isEqualTo("role");
        assertThat(fields.get(0).get("label")).isEqualTo("Role name");
        assertThat(fields.get(0).get("type")).isEqualTo("choice");
        assertThat(fields.get(0).get("options")).isEqualTo(List.of("air_defence", "assault"));
        assertThat(fields.get(1)).doesNotContainKey("options");
    }

    @Test
    void omitsAnEmptyParentList() {
        Map<String, Object> stored = NodeTypeRules.normalize(
                Map.of("root", false, "parents", List.of(), "fields", List.of()), "UNIT", KNOWN);
        assertThat(stored).doesNotContainKey("parents");
    }

    @Test
    void rejectsRulesThatWouldBreakAnOrbat() {
        assertThat(errors(Map.of("seeded", true))).containsEntry("properties.seeded", "Seeded types come from the catalogue.");
        assertThat(errors(Map.of("seeded", "yes"))).containsEntry("properties.seeded", "Must be true or false.");
        assertThat(errors(Map.of("root", "yes"))).containsEntry("properties.root", "Must be true or false.");
        assertThat(errors(Map.of("note", true))).containsEntry("properties.note", "This property is not supported.");
        assertThat(errors(Map.of("parents", "BRIGADE"))).containsEntry("properties.parents", "Parents must be a list of node type codes.");
        assertThat(errors(Map.of("parents", List.of("UNIT")))).containsEntry("properties.parents[0]", "A type cannot list itself as a parent.");
        assertThat(errors(Map.of("parents", List.of("MISSING")))).containsEntry("properties.parents[0]", "No node type uses this code.");
        assertThat(errors(Map.of("parents", List.of("BRIGADE", "brigade"))))
                .containsEntry("properties.parents[1]", "This parent is listed more than once.");
        assertThat(errors(Map.of("parents", List.of("bad code"))))
                .containsEntry("properties.parents[0]", "Use a letter, then letters, numbers, or underscores.");
        assertThat(errors(Map.of("fields", "nope"))).containsEntry("properties.fields", "Attributes must be a list.");
        assertThat(errors(Map.of("fields", List.of("nope")))).containsEntry("properties.fields[0]", "Each attribute must be an object.");

        Map<String, Object> reserved = field("name", "Name", "text", false, null);
        assertThat(errors(Map.of("fields", List.of(reserved)))).containsEntry("properties.fields[0].key", "This key is reserved.");
        assertThat(errors(Map.of("fields", List.of(field("parentId", "Parent", "text", false, null)))))
                .containsEntry("properties.fields[0].key", "This key is reserved.");
        assertThat(errors(Map.of("fields", List.of(field("1role", "Role", "text", false, null)))))
                .containsEntry("properties.fields[0].key", "Use a letter, then lowercase letters, numbers, or underscores.");
        assertThat(errors(Map.of("fields", List.of(field("role", "Role", "text", true, null), field("role", "Other", "text", false, null)))))
                .containsEntry("properties.fields[0].key", "Another attribute already uses this key.")
                .containsEntry("properties.fields[1].key", "Another attribute already uses this key.");
        assertThat(errors(Map.of("fields", List.of(field("role", "Role", "text", false, null), field("duty", " role ", "text", false, null)))))
                .containsEntry("properties.fields[1].label", "Another attribute already uses this label.");
        assertThat(errors(Map.of("fields", List.of(field("role", "   ", "text", false, null)))))
                .containsEntry("properties.fields[0].label", "Enter a label.");
        assertThat(errors(Map.of("fields", List.of(field("role", "Role", "flag", false, null)))))
                .containsEntry("properties.fields[0].type", "Choose text, number, or choice.");

        Map<String, Object> missingRequired = field("role", "Role", "text", false, null);
        missingRequired.remove("required");
        assertThat(errors(Map.of("fields", List.of(missingRequired))))
                .containsEntry("properties.fields[0].required", "Say whether this attribute is required.");
        assertThat(errors(Map.of("fields", List.of(field("role", "Role", "choice", true, null)))))
                .containsEntry("properties.fields[0].options", "Add at least one option.");
        assertThat(errors(Map.of("fields", List.of(field("role", "Role", "choice", true, List.of())))))
                .containsEntry("properties.fields[0].options", "Add at least one option.");
        assertThat(errors(Map.of("fields", List.of(field("role", "Role", "text", false, List.of("one"))))))
                .containsEntry("properties.fields[0].options", "Options are only for a choice.");
        assertThat(errors(Map.of("fields", List.of(field("role", "Role", "choice", true, List.of("!!!"))))))
                .containsEntry("properties.fields[0].options[0]", "Use letters or numbers.");
        assertThat(errors(Map.of("fields", List.of(field("role", "Role", "choice", true, List.of("Air Defence", "air defence"))))))
                .containsEntry("properties.fields[0].options[1]", "This option is listed more than once.");

        Map<String, Object> extra = field("role", "Role", "text", false, null);
        extra.put("helper", "nope");
        assertThat(errors(Map.of("fields", List.of(extra)))).containsEntry("properties.fields[0].helper", "This property is not supported.");
    }

    @Test
    void capsTheSizeOfTheDocument() {
        List<Map<String, Object>> fields = new ArrayList<>();
        for (int index = 0; index < NodeTypeRules.MAX_FIELDS + 1; index++) {
            fields.add(field("field_" + index, "Field " + index, "text", false, null));
        }
        assertThat(errors(Map.of("fields", fields))).containsEntry("properties.fields", "A node type can have at most 30 attributes.");

        List<String> options = new ArrayList<>();
        for (int index = 0; index < NodeTypeRules.MAX_OPTIONS + 1; index++) {
            options.add("option_" + index);
        }
        assertThat(errors(Map.of("fields", List.of(field("role", "Role", "choice", true, options)))))
                .containsEntry("properties.fields[0].options", "An attribute can have at most 40 options.");

        List<String> parents = new ArrayList<>();
        for (int index = 0; index < NodeTypeRules.MAX_PARENTS + 1; index++) {
            parents.add("BRIGADE");
        }
        assertThat(errors(Map.of("parents", parents))).containsKey("properties.parents");
    }

    @Test
    void messageStaysABadRequest() {
        assertThatThrownBy(() -> NodeTypeRules.normalize(Map.of("seeded", true), "UNIT", KNOWN))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The node type properties are not valid.");
    }

    private static Map<String, String> errors(Map<String, Object> properties) {
        try {
            NodeTypeRules.normalize(properties, "UNIT", KNOWN);
        } catch (BadRequestException exception) {
            return exception.getFieldErrors();
        }
        throw new AssertionError("Expected the properties to be rejected.");
    }

    private static Map<String, Object> field(String key, String label, String type, boolean required, List<String> options) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", key);
        field.put("label", label);
        field.put("type", type);
        field.put("required", required);
        if (options != null) {
            field.put("options", options);
        }
        return field;
    }
}
