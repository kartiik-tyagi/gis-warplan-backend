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
| role | Master | id, code, name, description, functionality_ids, standard |
| node_type | Master | id, code, name, description, properties, created_at, updated_at, created_by, updated_by |
| standard_orbat | Working | id, side_id, orbat_json, metadata, version, created_at, updated_at, updated_by |
| playing_orbat | Working | id, game_id, scenario_id (denormalized from game), side_id, orbat_json, metadata (draft\|finalized), version, created_at, updated_at, updated_by |
| scenario | Master | id, name, general_idea (text HTML), opening_narrative (text HTML), special_idea (text HTML), active, attached_file (uploaded file name; bytes under warplan.attachments.root/{id}/), created_at, updated_at, created_by |
| game | Working | id, scenario_id, name, date_time, sunrise, sunset, moonphases, description (text HTML), active, metadata, created_at, updated_at |
| app_user | Working | id, name, password_hash, role_ids, administered_side_id, active, theme_id, standard_orbat_id, standard_node_id, playing_orbat_id, playing_node_id, last_login_at, created_at, updated_at |
| user_session | Audit | id, user_id, login_at, logout_at, success, failure_reason, ip_address, user_agent, location_text, session_token_id, created_at (+ JWT expires_at / revoked_at for revoke) |
| audit_log | Audit | id, occurred_at, actor_user_id, session_id, action, entity_type, entity_id, summary, detail_json, ip_address, user_agent, request_id |
| gis_server / map_layer / map_view_defaults | Map | unchanged |

## Logic rules

- Country = **side**; capability = **functionality**.
- No soft-`active` on side, functionality, role, or node_type.
- Alliance = side `kind=alliance`, owned by **game** (`game_id`).
- Each **game** owns its **playing_orbat**(s). `scenario_id` on alliance side / playing_orbat is copied from `game.scenario_id` only.
- Scenario `active` / game `active` = which exercise is open. App user `active` = account enabled.
- `POST /scenarios/deactivate` clears all active scenarios and games (no open exercise).
- Scenario narrative fields and game `description` store sanitized HTML rich text (not plain-only).
- ORBAT: tree in `orbat_json`; lifecycle extras in `metadata`; nodes use `homeSideId`.

## DB note

Hibernate `ddl-auto: update` does not rename or drop columns. After a lock-breaking change, drop and recreate database `gis_warplan` once, then restart so seed rebuilds.
