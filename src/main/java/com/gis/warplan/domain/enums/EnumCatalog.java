package com.gis.warplan.domain.enums;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The fixed dropdown lists. Keys are URL identifiers. Table names stay on the entities.
 * An existing seeded row is never overwritten.
 */
public final class EnumCatalog {

    public record Seed(String code, String label, int sortOrder, String sideName) {
    }

    public record ListDef(String key, String label, boolean sideSpecific, Class<? extends EnumMutable> type,
            List<Seed> seeds) {
    }

    private static final List<ListDef> LISTS = List.of(
            plain("day-night", "Day / Night", EnumEntities.DayNight.class, "Day", "Night"),
            plain("waypoint-achieved", "Waypoint achieved", EnumEntities.WaypointAchieved.class, "No", "Yes"),
            plain("isr-payload", "ISR payload", EnumEntities.IsrPayload.class, "EO IR", "EO IR SAR", "EO", "SAR", "EO SAR"),
            plain("task-status", "Task status", EnumEntities.TaskStatus.class, "Pending", "Completed", "Terminated", "Cancelled"),
            plain("air-capability", "Air capability", EnumEntities.AirCapability.class, "Air parity", "Air supremacy", "Air superiority"),
            plain("game-state", "Game state", EnumEntities.GameState.class, "Game start", "Game stop", "Game pause"),
            plain("air-platform-type", "Air platform type", EnumEntities.AirPlatformType.class, "Aircraft", "Helicopter", "AWACS"),
            plain("naval-platform-type", "Naval platform type", EnumEntities.NavalPlatformType.class, "Surface", "Subsurface", "Aircraft", "Helicopter", "Site"),
            side("land-unit", "Land unit", EnumEntities.LandUnit.class, landUnits()),
            plain("land-unit-level", "Land unit level", EnumEntities.StandardLandUnitLevel.class, "Special", "Corps", "Bde", "CC", "Div", "UNA", "CAB"),
            plain("land-unit-type", "Land unit type", EnumEntities.StandardLandUnitType.class, "Special", "Armd", "Inf", "Mtn", "Rapid", "Arty", "Mech", "Engr", "AD", "Para", "LAT", "HAT", "Heavy", "Light", "Medium", "Mot Div", "Mech Div", "Armd Div", "Armd Regt", "Mech Regt", "Mot Inf Regt", "Rocket force"),
            plain("land-unit-variant", "Land unit variant", EnumEntities.LandUnitVariant.class, "Normal", "Amphibious", "HRU", "Ind"),
            plain("hierarchy-level", "Hierarchy level", EnumEntities.LandUnitHierarchyLevel.class, "Level 0", "Level 1", "Level A", "Levels"),
            plain("casualty-type", "Casualty type", EnumEntities.LandUnitCasualtyType.class, "Person", "Vehicle", "Equipment"),
            plain("standard-operation", "Standard operation", EnumEntities.LandUnitStandardOperation.class,
                    "Isolation and investment node", "Advance astride", "Encounter", "Dog", "Our FDM", "Link up", "HBABOPS", "ESTOP", "Pivot", "Info arm",
                    "Secure crossing", "Hand over captured area", "Consolidate", "Be prepared to destroy or degrade", "Relieve formation", "Counter attack",
                    "Hold defences", "RAID", "De-isolation", "Mob and induct", "Counter penetration", "Capture of Bn less two coy", "Secure access to main",
                    "Jenance", "Counter infiltration", "Heavy breakthrough", "Deliberate attack", "Isolation and investment of Bde minus node", "Capture of Bn",
                    "Capture of Bde minus node"),
            plain("lrv-explosive-type", "LRV explosive type", EnumEntities.LrvExplosiveType.class, "Conventional", "Nuclear", "Both"),
            plain("halt-type", "Halt type", EnumEntities.HaltType.class, "Short halt", "Long halt", "No halt"),
            plain("in-out-uavp", "In / out UAVP", EnumEntities.InOutUavp.class, "In", "Out"),
            plain("burst-type", "Burst type", EnumEntities.BurstType.class, "Airburst", "Ground burst"),
            plain("cato-target", "CATO target", EnumEntities.CatoTarget.class, "Airbase", "Underground fuel tanks", "Parked bombers", "Radar", "Tank", "Tank column",
                    "Combat team", "Road", "Rail", "Canal", "River", "Embankment targets", "Bridge", "Refinery", "Railway marshalling yards", "Logistics node",
                    "FARP", "Wpn storage area", "C2 node", "Div adm area", "Corps maint area", "Fwd assy area", "Veh column", "High value asset"),
            plain("message-type", "Message type", EnumEntities.MessageType.class, "Information", "Request", "Response"),
            plain("cato-mission-type", "CATO mission type", EnumEntities.CatoMissionType.class, "Fighter sweep", "Tied escort", "Air intercept", "CAP", "Deep strike",
                    "CAS", "Battlefield air interdiction", "SEAD", "Maritime ops", "Fighter escort", "Search and rescue", "Casualty evaluation"),
            plain("theatre-type", "Theatre type", EnumEntities.TheatreType.class, "Theatre", "Sub theatre"),
            plain("op-order-task", "Op order task", EnumEntities.OpOrderTask.class, "Establish En yg", "Capture area", "Flak protection", "Isolate", "Occupy def",
                    "Country attack", "Country protection"),
            plain("weather", "Weather", EnumEntities.WeatherType.class, "Good", "Bad", "Moderate"),
            plain("theatre-wing", "Theatre wing", EnumEntities.TheatreWingType.class, "Navy", "Army", "Airforce"),
            plain("air-weapon-type", "Air weapon type", EnumEntities.StandardAirWeaponType.class, "LGB", "Free fall bomb", "Cluster bomb", "ARM", "PGM",
                    "Rocket 57 mm", "Rocket 80 mm", "Gun", "ATGM", "Drop tank", "Anti ship missile", "Anti surface missile", "Air-to-air missile", "Other"),
            plain("naval-unit-status", "Naval unit status", EnumEntities.NavalUnitStatus.class, "Available", "Terminated"),
            plain("movement-type", "Movement type", EnumEntities.LandUnitMovementType.class, "Road", "Rail", "Air"),
            side("absb-unit-level", "ABSB unit level", EnumEntities.AbsbUnitLevel.class, absbLevels()),
            plain("islop-type", "ISLOP type", EnumEntities.IslopType.class, "Hand breaching", "One trawl with plough", "CLMC"),
            plain("assault-lane-operation", "Assault lane operation", EnumEntities.AssaultLaneOpType.class, "Assault lane"),
            plain("vslop-type", "VSLOP type", EnumEntities.VslopType.class, "Hand breaching", "CLME", "One trawl with plough"),
            plain("radar-type", "Radar type", EnumEntities.RadarType.class, "Pulse doppler", "Early warning", "Surveillance radar", "PESA", "AESA"),
            plain("radar-frequency-band", "Radar frequency band", EnumEntities.RadarFrequencyBand.class, "S band", "L band", "VHF band", "VUHF"),
            plain("request-status", "Request status", EnumEntities.RequestStatus.class, "Pending", "Accept", "Reject"),
            plain("land-unit-involved", "Land unit involved", EnumEntities.ScenarioLandUnitInvolvedStatus.class, "Not involved", "Involved"),
            plain("deploy-type", "Deploy type", EnumEntities.DeployType.class, "SSM", "SAM", "Mines"));

    static {
        Set<String> keys = new LinkedHashSet<>();
        for (ListDef list : LISTS) {
            if (!keys.add(list.key())) {
                throw new IllegalStateException("Duplicate enum list " + list.key());
            }
            Set<String> identities = new LinkedHashSet<>();
            for (Seed seed : list.seeds()) {
                if (seed.code().length() > 40 || !seed.code().matches("^[A-Z][A-Z0-9_]{0,39}$")) {
                    throw new IllegalStateException("Bad enum code " + seed.code() + " for " + seed.label());
                }
                String identity = list.sideSpecific() ? seed.sideName() + ":" + seed.code() : seed.code();
                if (!identities.add(identity)) {
                    throw new IllegalStateException("Duplicate enum seed " + list.key() + " " + identity);
                }
            }
        }
    }

    private EnumCatalog() {
    }

    public static List<ListDef> lists() {
        return LISTS;
    }

    public static Optional<ListDef> find(String key) {
        if (key == null) {
            return Optional.empty();
        }
        return LISTS.stream().filter(list -> list.key().equals(key)).findFirst();
    }

    private static ListDef plain(String key, String label, Class<? extends EnumMutable> type, String... labels) {
        return new ListDef(key, label, false, type, numbered(null, labels));
    }

    private static ListDef side(String key, String label, Class<? extends EnumMutable> type, List<Seed> seeds) {
        return new ListDef(key, label, true, type, List.copyOf(seeds));
    }

    private static List<Seed> numbered(String sideName, String... labels) {
        List<Seed> seeds = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            seeds.add(new Seed(codeOf(labels[i]), labels[i], i, sideName));
        }
        return List.copyOf(seeds);
    }

    private static List<Seed> landUnits() {
        List<Seed> seeds = new ArrayList<>();
        addSide(seeds, "Blue", "Corps", "Armd Div", "Inf Div", "Rapid Div", "Mtn Div", "Arty Div", "Armd Bde", "Inf Bde", "Arty Bde", "Mech Bde",
                "Engr Bde", "AD Bde", "Inf I Bde", "Armd I Bde", "AD I Bde", "Arty I Bde", "Para I Bde", "Mtn I Bde", "Mech I Bde", "Amphibious Bde", "HRU",
                "Engg Regt");
        addSide(seeds, "Red", "Corps", "Armd Div", "Inf Div", "Mtn Div", "Arty Div", "Mech Div", "Armed Bde", "Inf Bde", "Arty Bde", "Mech Bde", "Engr Bde",
                "AD Bde", "LAT", "HAT", "Armed I Bde", "Inf I Bde", "Arty I Bde", "Mtn Bde", "Mtn I Bde", "Mech I Bde", "AD I Bde");
        addSide(seeds, "Yellow", "GA", "CAB Heavy", "CAB Light", "CAB Medium", "Combat Command", "Mot Div", "Mech Div", "Armd Div", "Armd Regt", "Mech Regt",
                "Mot Inf Regt", "Rocket Force", "SS Force", "GNC", "CAB AF");
        return List.copyOf(seeds);
    }

    private static List<Seed> absbLevels() {
        List<Seed> seeds = new ArrayList<>();
        addSide(seeds, "Blue", "Bde", "Bn", "Coy");
        addSide(seeds, "Red", "Bde", "Bn", "Coy");
        addSide(seeds, "Yellow", "Div", "Bde", "Bn", "Coy");
        return List.copyOf(seeds);
    }

    private static void addSide(List<Seed> seeds, String sideName, String... labels) {
        int order = seeds.size();
        for (String label : labels) {
            seeds.add(new Seed(codeOf(label), label, order++, sideName));
        }
    }

    static String codeOf(String label) {
        String code = label.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.length() > 40) {
            code = code.substring(0, 40).replaceAll("_+$", "");
        }
        return code;
    }
}
