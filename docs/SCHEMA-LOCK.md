# GIS Warplan — Schema Lock

Single source of truth for domain tables. Entity, API, and UI work must match this document.
Do not invent substitute fields (`description` / `configJson` / `status` on scenario or game).

## Catalogue

| Table | Kind | Attributes |
| --- | --- | --- |
| theme | Master | id, name, description, config_json, created_at, updated_at |
| side | Master | id, name, description, kind (country\|alliance), theme_id, icon, colour, game_id (alliance only), scenario_id (denormalized from game, alliance only), created_at, updated_at |
| alliance_member | Link | id, alliance_side_id, country_side_id |
| functionality | Master | id, code, name, description |
| role | Master | id, code (internal system key only — not on role API), name, description, functionality_ids, standard |
| node_type | Master | id, code, name, description, properties, created_at, updated_at, created_by, updated_by |
| standard_orbat | Working | id, side_id, orbat_json, metadata, version, created_at, updated_at, updated_by |
| playing_orbat | Working | id, game_id, scenario_id (denormalized from game), side_id, orbat_json, metadata (draft\|finalized), version, created_at, updated_at, updated_by |
| scenario | Master | id, name, general_idea (text HTML), opening_narrative (text HTML), special_idea (text HTML), active, attached_file (display name), attachment_asset_id (asset row; bytes live in the asset store), created_at, updated_at, created_by |
| moon_phase | Master | code (primary key, stored on game.moonphases), label, sort_order, icon (storage key in the icon store). Seeded once. A blank icon is filled; a chosen icon is left alone. |
| enum_* | Master | One table per dropdown list: enum_day_night, enum_waypoint_achieved, enum_isr_payload, enum_task_status, enum_air_capability, enum_game_state, enum_air_platform_type, enum_naval_platform_type, enum_land_unit, enum_standard_land_unit_level, enum_standard_land_unit_type, enum_land_unit_variant, enum_land_unit_hierarchy_level, enum_land_unit_casualty_type, enum_land_unit_standard_operation, enum_lrv_explosive_type, enum_halt_type, enum_in_out_uavp, enum_burst_type, enum_cato_target, enum_message_type, enum_cato_mission_type, enum_theatre_type, enum_op_order_task, enum_weather_type, enum_theatre_wing_type, enum_standard_air_weapon_type, enum_naval_unit_status, enum_land_unit_movement_type, enum_absb_unit_level, enum_islop_type, enum_assault_lane_op_type, enum_vslop_type, enum_radar_type, enum_radar_frequency_band, enum_request_status, enum_scenario_land_unit_involved_status, enum_deploy_type. Columns: code (primary key, length 40), label (length 120), sort_order, active. enum_land_unit and enum_absb_unit_level use primary key (code, side_id) and reference side. The same code and label may exist for another side. Seed inserts a missing code and does not overwrite an existing row. Side rows are inserted only when exactly one country side is named Blue, Red, or Yellow. |
| game | Working | id, scenario_id, name, date_time, sunrise, sunset, moonphases (moon_phase.code, empty when none), description (text HTML), active, metadata, created_at, updated_at |
| app_user | Working | id, name, password_hash, role_ids, administered_side_id, active, theme_id, standard_orbat_id, standard_node_id, playing_orbat_id, playing_node_id, last_login_at, created_at, updated_at |
| user_session | Audit | id, user_id, login_at, logout_at, success, failure_reason, ip_address, user_agent, location_text, session_token_id, created_at (+ JWT expires_at / revoked_at for revoke) |
| audit_log | Audit | id, occurred_at, actor_user_id, session_id, action, entity_type, entity_id, summary, detail_json, ip_address, user_agent, request_id |
| asset_management | Config | id, path (base folder, default asset-repository), labels (legacy JSON, flattened once to a public path → label map), icon_max_bytes, image_max_bytes, document_max_bytes, last_updated, updated_by — **single row** |
| asset | Working | id, kind (ICON\|IMAGE\|DOCUMENT\|APPLICATION), slot (LOGO\|FAVICON\|LOGIN_BACKGROUND or null), storage_key, original_name, label (one public name, null falls back to the file name), media_type, size_bytes, created_by, created_at, updated_at |
| gis_server / map_layer | Map | unchanged |
| map_view_defaults | Map | centre, zoom, data_crs, view_crs, elevation_layer_id, elevation_attribute, elevation_positive_up |

## Logic rules

- Country = **side**; capability = **functionality**.
- No soft-`active` on side, functionality, role, or node_type.
- Alliance = side `kind=alliance`, owned by **game** (`game_id`).
- Each **game** owns its **playing_orbat**(s). `scenario_id` on alliance side / playing_orbat is copied from `game.scenario_id` only.
- Scenario `active` / game `active` = which exercise is open. App user `active` = account enabled.
- `POST /scenarios/deactivate` clears all active scenarios and games (no open exercise).
- Scenario narrative fields and game `description` store sanitized HTML rich text (not plain-only).
- ORBAT: tree in `orbat_json`; lifecycle extras in `metadata`; nodes use `homeSideId`.
- Assets: one store under `asset_management.path` (default `asset-repository`, any writable folder on this server). Kinds live in `icons/`, `images/`, `documents/`, and `application/`. Icons are SVG and PNG. Images are PNG, JPG, GIF, and WebP. Documents are TXT, DOC, DOCX, and PDF. Default limits are 512 KB, 10 MB, and 50 MB, and each limit is editable up to 50 MB. A label is public. A nested per-user label map is flattened once on startup; the lowest user id is kept. Logo, favicon, and login background are application slots. Scenario attachments are assets. Path, limit, upload, and label writes need `MANAGE_ICONS`.

## DB note

Hibernate `ddl-auto: update` does not rename or drop columns. After a lock-breaking change, drop and recreate database `gis_warplan` once, then restart so seed rebuilds.
