package com.gis.warplan.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.gis.warplan.domain.NodeType;
import com.gis.warplan.domain.PlayingOrbat;
import com.gis.warplan.domain.Side;
import com.gis.warplan.domain.StandardOrbat;
import com.gis.warplan.repository.NodeTypeRepository;
import com.gis.warplan.repository.PlayingOrbatRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.repository.StandardOrbatRepository;
import com.gis.warplan.service.OrbatSupport;
import com.gis.warplan.service.SideService;

/**
 * Seeds the ORBAT catalogue into {@code node_type}. Existing codes are left untouched, so a renamed type or a
 * chosen icon survives a restart. Icons are files in the asset-management folder.
 */
@Component
@Order(15)
public class NodeTypeCatalog implements ApplicationRunner {

    private static final Logger log = LogManager.getLogger(NodeTypeCatalog.class);

    private final NodeTypeRepository nodeTypes;
    private final StandardOrbatRepository standardOrbats;
    private final PlayingOrbatRepository playingOrbats;
    private final SideRepository sides;
    private final TransactionTemplate transaction;

    public NodeTypeCatalog(NodeTypeRepository nodeTypes, StandardOrbatRepository standardOrbats,
            PlayingOrbatRepository playingOrbats, SideRepository sides, TransactionTemplate transaction) {
        this.nodeTypes = nodeTypes;
        this.standardOrbats = standardOrbats;
        this.playingOrbats = playingOrbats;
        this.sides = sides;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments args) {
        transaction.executeWithoutResult(status -> seed());
    }

    private void seed() {
        List<Seed> rows = List.of(
                root("COUNTRY", "Country", "The side at the root of a standard ORBAT", "square.svg", List.of("ALLIANCE")),
                root("ALLIANCE", "Alliance", "The side at the root of an alliance ORBAT", "square.svg", List.of()),
                type("THEATRE", "Theatre", "Top land or joint command under the side", "square.svg",
                        List.of("COUNTRY", "ALLIANCE"), List.of(
                                choice("theatre_type", "Theatre type", true, "army_command", "joint", "strategic"),
                                text("area_name", "Area name", false),
                                text("commander_rank", "Commander rank", false))),
                type("CORPS", "Corps", "Formation under a theatre", "circle.svg",
                        List.of("THEATRE"), List.of(
                                choice("corps_type", "Corps type", true, "strike", "holding", "mountain", "air_defence", "artillery", "reserve"),
                                text("commander_rank", "Commander rank", false),
                                number("potential", "Potential", false))),
                type("DIVISION", "Division", "Formation under a corps, or under a theatre as theatre troops", "circle.svg",
                        List.of("CORPS", "THEATRE"), List.of(
                                choice("division_type", "Division type", true, "infantry", "mountain", "armoured", "rapid", "artillery", "air_defence", "amphibious"),
                                text("commander_rank", "Commander rank", false),
                                number("potential", "Potential", false))),
                type("BRIGADE", "Brigade", "Formation under a division, or under a corps when independent", "circle.svg",
                        List.of("DIVISION", "CORPS"), List.of(
                                choice("brigade_type", "Brigade type", true, "infantry", "armoured", "mechanised", "artillery", "engineer", "air_defence", "signals", "independent"),
                                choice("independent", "Independent", true, "yes", "no"),
                                text("commander_rank", "Commander rank", false),
                                number("potential", "Potential", false))),
                type("BATTALION", "Battalion", "Battalion or regiment under a brigade, division, or corps", "circle.svg",
                        List.of("BRIGADE", "DIVISION", "CORPS"), List.of(
                                choice("battalion_type", "Battalion type", true, "infantry", "mechanised", "parachute", "armoured", "artillery", "engineer", "signals", "air_defence", "services"),
                                text("regiment_name", "Regiment name", false),
                                text("commander_rank", "Commander rank", false),
                                number("potential", "Potential", true),
                                number("authorised_strength", "Authorised strength", false))),
                type("LAND_UNIT", "Land unit", "Body plotted on the map", "circle.svg",
                        List.of("BATTALION", "BRIGADE"), List.of(
                                choice("unit_type", "Unit type", true, "company", "squadron", "battery", "platoon", "troop", "detachment"),
                                choice("arm", "Arm", true, "infantry", "mechanised", "parachute", "armoured", "artillery", "engineer", "signals", "air_defence", "services"),
                                choice("role", "Role", true, "assault", "defence", "reserve", "reconnaissance", "fire_support", "engineer", "air_defence", "logistics"),
                                number("potential", "Potential", true),
                                choice("mobility", "Mobility", true, "foot", "wheeled", "tracked", "air"),
                                text("primary_equipment", "Primary equipment", false),
                                number("equipment_quantity", "Equipment quantity", false))),
                type("AOC", "Air command", "Air command headquarters. Airbases hang under it", "square.svg",
                        List.of("THEATRE", "COUNTRY", "ALLIANCE"), List.of(
                                choice("aoc_type", "Air command type", true, "command", "operations_centre", "forward"),
                                text("area_name", "Area name", false),
                                text("commander_rank", "Commander rank", false))),
                type("AIRBASE", "Airbase", "Air station under an air command or a theatre", "triangle.svg",
                        List.of("AOC", "THEATRE"), List.of(
                                choice("base_type", "Base type", true, "fighter", "transport", "helicopter", "mixed", "forward", "uav"),
                                choice("activation", "Activation", true, "permanent", "forward", "emergency"),
                                number("elevation_m", "Elevation (m)", true),
                                number("runway_count", "Runway count", false),
                                number("runway_length_m", "Runway length (m)", false),
                                text("primary_aircraft", "Primary aircraft", false),
                                number("aircraft_potential", "Aircraft potential", false))),
                type("GOC", "Land headquarters", "Headquarters of one theatre, corps, or division", "square.svg",
                        List.of("THEATRE", "CORPS", "DIVISION"), List.of(
                                choice("formation_type", "Formation type", true, "theatre", "corps", "division"),
                                text("formation_id", "Formation", true),
                                text("commander_rank", "Commander rank", false))),
                type("NAVAL_PORT", "Naval port", "Naval base or port. Submarines hang under it", "triangle.svg",
                        List.of("THEATRE", "COUNTRY", "ALLIANCE"), List.of(
                                choice("port_type", "Port type", true, "naval_base", "commercial", "forward", "anchorage"),
                                choice("activation", "Activation", true, "permanent", "forward", "emergency"),
                                number("harbor_depth_m", "Harbour depth (m)", true),
                                number("berth_count", "Berth count", false),
                                text("primary_vessel", "Primary vessel", false),
                                number("vessel_potential", "Vessel potential", false))),
                type("NAVAL_SUBMARINE", "Submarine", "Boat plotted on the map. Parent is its home naval port", "triangle.svg",
                        List.of("NAVAL_PORT"), List.of(
                                choice("submarine_type", "Submarine type", true, "ssn", "ssk", "ssbn", "ssgn"),
                                choice("role", "Role", true, "attack", "patrol", "deterrent", "special_operations"),
                                choice("propulsion", "Propulsion", true, "nuclear", "diesel_electric", "aip"),
                                number("potential", "Potential", true),
                                text("pennant", "Pennant", false),
                                text("primary_weapon", "Primary weapon", false),
                                number("weapon_quantity", "Weapon quantity", false))));
        for (Seed seed : rows) {
            NodeType existing = nodeTypes.findByCode(seed.code()).orElse(null);
            if (existing != null) {
                if (existing.getProperties() != null && Boolean.TRUE.equals(existing.getProperties().get("seeded"))) {
                    existing.getProperties().put("parents", seed.properties().get("parents"));
                    existing.getProperties().put("root", seed.properties().get("root"));
                    existing.getProperties().put("fields", seed.properties().get("fields"));
                    nodeTypes.save(existing);
                }
                continue;
            }
            NodeType type = new NodeType();
            type.setCode(seed.code());
            type.setName(seed.name());
            type.setDescription(seed.description());
            type.setIconPath(seed.icon());
            type.setProperties(seed.properties());
            nodeTypes.save(type);
            log.info("node_type.seeded code={}", seed.code());
        }
        assignMissingRootTypes();
    }

    /** Sides created before the catalogue have a root with no type. Give them Country or Alliance. */
    private void assignMissingRootTypes() {
        Long country = nodeTypes.findByCode("COUNTRY").map(NodeType::getId).orElse(null);
        Long alliance = nodeTypes.findByCode("ALLIANCE").map(NodeType::getId).orElse(null);
        if (country != null) {
            for (StandardOrbat orbat : standardOrbats.findAll()) {
                if (assignRoot(orbat.getOrbatJson(), country)) {
                    orbat.setOrbatJson(OrbatSupport.document(OrbatSupport.nodesOf(orbat.getOrbatJson())));
                    standardOrbats.save(orbat);
                    log.info("node_type.root_assigned orbatId={} code=COUNTRY", orbat.getId());
                }
            }
        }
        if (alliance == null || country == null) {
            return;
        }
        for (PlayingOrbat orbat : playingOrbats.findAll()) {
            Side side = orbat.getSideId() == null ? null : sides.findById(orbat.getSideId()).orElse(null);
            Long typeId = side != null && SideService.KIND_ALLIANCE.equals(side.getKind()) ? alliance : country;
            if (assignRoot(orbat.getOrbatJson(), typeId)) {
                orbat.setOrbatJson(OrbatSupport.document(OrbatSupport.nodesOf(orbat.getOrbatJson())));
                playingOrbats.save(orbat);
                log.info("node_type.root_assigned playingId={} typeId={}", orbat.getId(), typeId);
            }
        }
    }

    private static boolean assignRoot(Map<String, Object> orbatJson, Long typeId) {
        boolean changed = false;
        for (Map<String, Object> node : OrbatSupport.nodesOf(orbatJson)) {
            if (node.get("parentId") == null && OrbatSupport.asLong(node.get("nodeTypeId")) == null) {
                node.put("nodeTypeId", typeId);
                changed = true;
            }
        }
        return changed;
    }

    private static Seed root(String code, String name, String description, String icon, List<String> parents) {
        Map<String, Object> properties = base(true);
        properties.put("parents", parents);
        properties.put("fields", List.of());
        return new Seed(code, name, description, icon, properties);
    }

    private static Seed type(String code, String name, String description, String icon, List<String> parents, List<Map<String, Object>> extra) {
        Map<String, Object> properties = base(false);
        properties.put("parents", parents);
        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(text("code", "Code", true));
        fields.add(text("short_name", "Short name", false));
        fields.addAll(extra);
        fields.add(text("location_name", "Location", true));
        fields.add(number("latitude", "Latitude", true));
        fields.add(number("longitude", "Longitude", true));
        fields.add(text("remarks", "Remarks", false));
        properties.put("fields", fields);
        return new Seed(code, name, description, icon, properties);
    }

    private static Map<String, Object> base(boolean root) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("seeded", true);
        properties.put("root", root);
        return properties;
    }

    private static Map<String, Object> text(String key, String label, boolean required) {
        return field(key, label, "text", required, List.of());
    }

    private static Map<String, Object> number(String key, String label, boolean required) {
        return field(key, label, "number", required, List.of());
    }

    private static Map<String, Object> choice(String key, String label, boolean required, String... options) {
        return field(key, label, "choice", required, List.of(options));
    }

    private static Map<String, Object> field(String key, String label, String type, boolean required, List<String> options) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("key", key);
        field.put("label", label);
        field.put("type", type);
        field.put("required", required);
        if (!options.isEmpty()) {
            field.put("options", options);
        }
        return field;
    }

    private record Seed(String code, String name, String description, String icon, Map<String, Object> properties) {
    }
}
