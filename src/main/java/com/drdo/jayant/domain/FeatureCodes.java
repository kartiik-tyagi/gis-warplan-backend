package com.drdo.jayant.domain;

import java.util.List;

/**
 * Development-time feature catalogue. Must stay identical to
 * frontend {@code src/shared/auth/featureCodes.js}.
 */
public final class FeatureCodes {

    public static final String MAP_VIEW = "MAP_VIEW";
    public static final String MANAGE_THEMES = "MANAGE_THEMES";
    public static final String MANAGE_TEAMS = "MANAGE_TEAMS";
    public static final String MANAGE_NODE_TYPES = "MANAGE_NODE_TYPES";
    public static final String MANAGE_ROLES = "MANAGE_ROLES";
    public static final String MANAGE_USERS = "MANAGE_USERS";
    public static final String MARK_AS_USER = "MARK_AS_USER";
    public static final String MANAGE_STANDARD_ORBAT = "MANAGE_STANDARD_ORBAT";
    public static final String MANAGE_PLAYING_ORBAT = "MANAGE_PLAYING_ORBAT";
    public static final String MANAGE_ALLIANCE = "MANAGE_ALLIANCE";
    public static final String MANAGE_SCENARIO = "MANAGE_SCENARIO";
    public static final String MANAGE_GAME = "MANAGE_GAME";
    public static final String MANAGE_ICONS = "MANAGE_ICONS";
    public static final String VIEW_AUDIT = "VIEW_AUDIT";
    public static final String CHOOSE_THEME = "CHOOSE_THEME";

    public static final List<String> ALL = List.of(
            MAP_VIEW,
            MANAGE_THEMES,
            MANAGE_TEAMS,
            MANAGE_NODE_TYPES,
            MANAGE_ROLES,
            MANAGE_USERS,
            MARK_AS_USER,
            MANAGE_STANDARD_ORBAT,
            MANAGE_PLAYING_ORBAT,
            MANAGE_ALLIANCE,
            MANAGE_SCENARIO,
            MANAGE_GAME,
            MANAGE_ICONS,
            VIEW_AUDIT,
            CHOOSE_THEME);

    private FeatureCodes() {
    }
}
