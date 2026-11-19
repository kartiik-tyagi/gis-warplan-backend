package com.gis.warplan.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.AppUser;
import com.gis.warplan.domain.Game;
import com.gis.warplan.domain.NodeType;
import com.gis.warplan.domain.PlayingOrbat;
import com.gis.warplan.domain.Side;
import com.gis.warplan.repository.AppUserRepository;
import com.gis.warplan.repository.NodeTypeRepository;
import com.gis.warplan.repository.PlayingOrbatRepository;
import com.gis.warplan.repository.SideRepository;
import com.gis.warplan.security.UserPrincipal;

/**
 * The live map picture for the active game. APEX sees every finalized playing ORBAT. Anyone else sees the
 * one finalized playing ORBAT assigned to them, which is their whole team or their whole alliance.
 */
@Service
public class SituationService {

    private static final String FALLBACK_COLOUR = "#2f6fed";

    private final ActivationService activation;
    private final AccessService access;
    private final AppUserRepository users;
    private final PlayingOrbatRepository playingOrbats;
    private final SideRepository sides;
    private final NodeTypeRepository nodeTypes;

    public SituationService(ActivationService activation, AccessService access, AppUserRepository users,
            PlayingOrbatRepository playingOrbats, SideRepository sides, NodeTypeRepository nodeTypes) {
        this.activation = activation;
        this.access = access;
        this.users = users;
        this.playingOrbats = playingOrbats;
        this.sides = sides;
        this.nodeTypes = nodeTypes;
    }

    @Transactional(readOnly = true)
    public Picture current() {
        access.requirePrincipal();
        Optional<Game> game = activation.activeGame();
        if (game.isEmpty()) {
            return new Picture(null, List.of());
        }
        List<PlayingOrbat> visible = new ArrayList<>(visibleOrbats(game.get().getId()));
        visible.sort(Comparator.comparing(PlayingOrbat::getId));
        return new Picture(game.get().getId(), views(visible));
    }

    private List<PlayingOrbat> visibleOrbats(Long gameId) {
        if (access.isAdministrator()) {
            return playingOrbats.findByGameId(gameId).stream().filter(ActivationService::isFinalized).toList();
        }
        UserPrincipal principal = access.requirePrincipal();
        AppUser user = users.findById(principal.userId()).orElse(null);
        if (user == null || user.getPlayingOrbatId() == null) {
            return List.of();
        }
        return playingOrbats.findById(user.getPlayingOrbatId())
                .filter(orbat -> gameId.equals(orbat.getGameId()))
                .filter(ActivationService::isFinalized)
                .map(List::of)
                .orElse(List.of());
    }

    private List<OrbatView> views(List<PlayingOrbat> orbats) {
        if (orbats.isEmpty()) {
            return List.of();
        }
        Map<Long, NodeType> typeById = new LinkedHashMap<>();
        Map<Long, Side> sideById = new LinkedHashMap<>();
        for (PlayingOrbat orbat : orbats) {
            if (orbat.getSideId() != null) {
                sideById.put(orbat.getSideId(), null);
            }
            for (Map<String, Object> node : OrbatSupport.nodesOf(orbat.getOrbatJson())) {
                Long typeId = OrbatSupport.asLong(node.get("nodeTypeId"));
                Long homeSideId = OrbatSupport.asLong(node.get("homeSideId"));
                if (typeId != null) {
                    typeById.put(typeId, null);
                }
                if (homeSideId != null) {
                    sideById.put(homeSideId, null);
                }
            }
        }
        if (!typeById.isEmpty()) {
            for (NodeType type : nodeTypes.findAllById(typeById.keySet())) {
                typeById.put(type.getId(), type);
            }
        }
        if (!sideById.isEmpty()) {
            for (Side side : sides.findAllById(sideById.keySet())) {
                sideById.put(side.getId(), side);
            }
        }
        List<OrbatView> views = new ArrayList<>();
        for (PlayingOrbat orbat : orbats) {
            Side side = orbat.getSideId() == null ? null : sideById.get(orbat.getSideId());
            boolean alliance = side != null && SideService.KIND_ALLIANCE.equals(side.getKind());
            String colour = side != null && side.getColour() != null && !side.getColour().isBlank()
                    ? side.getColour()
                    : FALLBACK_COLOUR;
            List<Map<String, Object>> nodes = new ArrayList<>();
            for (Map<String, Object> node : OrbatSupport.nodesOf(orbat.getOrbatJson())) {
                nodes.add(present(node, typeById, sideById));
            }
            views.add(new OrbatView(orbat.getId(), orbat.getName(), orbat.getSideId(), colour, alliance, nodes));
        }
        return views;
    }

    private static Map<String, Object> present(Map<String, Object> node, Map<Long, NodeType> typeById,
            Map<Long, Side> sideById) {
        Map<String, Object> copy = new LinkedHashMap<>(node);
        NodeType type = typeById.get(OrbatSupport.asLong(node.get("nodeTypeId")));
        Side home = sideById.get(OrbatSupport.asLong(node.get("homeSideId")));
        Object icon = copy.get("iconPath");
        if (!(icon instanceof String text) || text.isBlank()) {
            if (type != null && type.getIconPath() != null && !type.getIconPath().isBlank()) {
                copy.put("iconPath", type.getIconPath());
            }
        }
        copy.put("nodeTypeName", type == null ? "" : type.getName());
        copy.put("homeSideName", home == null ? "" : home.getName());
        return copy;
    }

    public record Picture(Long gameId, List<OrbatView> orbats) {
    }

    public record OrbatView(Long playingOrbatId, String name, Long sideId, String colour, boolean alliance,
            List<Map<String, Object>> nodes) {
    }
}
