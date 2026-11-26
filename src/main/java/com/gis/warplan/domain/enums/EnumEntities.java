package com.gis.warplan.domain.enums;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** One entity per dropdown list. The catalogue is the only place that names these classes. */
public final class EnumEntities {

    private EnumEntities() {
    }

    @Entity
    @Table(name = "enum_day_night")
    public static class DayNight extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_waypoint_achieved")
    public static class WaypointAchieved extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_isr_payload")
    public static class IsrPayload extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_task_status")
    public static class TaskStatus extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_air_capability")
    public static class AirCapability extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_game_state")
    public static class GameState extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_air_platform_type")
    public static class AirPlatformType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_naval_platform_type")
    public static class NavalPlatformType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_land_unit")
    @IdClass(EnumSideKey.class)
    public static class LandUnit extends SideEnum {
    }

    @Entity
    @Table(name = "enum_standard_land_unit_level")
    public static class StandardLandUnitLevel extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_standard_land_unit_type")
    public static class StandardLandUnitType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_land_unit_variant")
    public static class LandUnitVariant extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_land_unit_hierarchy_level")
    public static class LandUnitHierarchyLevel extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_land_unit_casualty_type")
    public static class LandUnitCasualtyType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_land_unit_standard_operation")
    public static class LandUnitStandardOperation extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_lrv_explosive_type")
    public static class LrvExplosiveType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_halt_type")
    public static class HaltType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_in_out_uavp")
    public static class InOutUavp extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_burst_type")
    public static class BurstType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_cato_target")
    public static class CatoTarget extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_message_type")
    public static class MessageType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_cato_mission_type")
    public static class CatoMissionType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_theatre_type")
    public static class TheatreType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_op_order_task")
    public static class OpOrderTask extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_weather_type")
    public static class WeatherType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_theatre_wing_type")
    public static class TheatreWingType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_standard_air_weapon_type")
    public static class StandardAirWeaponType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_naval_unit_status")
    public static class NavalUnitStatus extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_land_unit_movement_type")
    public static class LandUnitMovementType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_absb_unit_level")
    @IdClass(EnumSideKey.class)
    public static class AbsbUnitLevel extends SideEnum {
    }

    @Entity
    @Table(name = "enum_islop_type")
    public static class IslopType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_assault_lane_op_type")
    public static class AssaultLaneOpType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_vslop_type")
    public static class VslopType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_radar_type")
    public static class RadarType extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_radar_frequency_band")
    public static class RadarFrequencyBand extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_request_status")
    public static class RequestStatus extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_scenario_land_unit_involved_status")
    public static class ScenarioLandUnitInvolvedStatus extends PlainEnum {
    }

    @Entity
    @Table(name = "enum_deploy_type")
    public static class DeployType extends PlainEnum {
    }
}
