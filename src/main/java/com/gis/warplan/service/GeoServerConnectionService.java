package com.gis.warplan.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gis.warplan.domain.GisServer;
import com.gis.warplan.repository.GisServerRepository;
import com.gis.warplan.service.geoserver.GeoServerException;
import com.gis.warplan.service.geoserver.GeoServerRestClient;
import com.gis.warplan.service.geoserver.GeoServerRestClient.Connection;
import com.gis.warplan.web.BadRequestException;
import com.gis.warplan.web.NotFoundException;

@Service
public class GeoServerConnectionService {

    private final GisServerRepository servers;
    private final SecretCryptoService crypto;
    private final GeoServerRestClient client;

    public GeoServerConnectionService(GisServerRepository servers, SecretCryptoService crypto,
            GeoServerRestClient client) {
        this.servers = servers;
        this.crypto = crypto;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public GisServer requireServer(Long id) {
        return servers.findById(id).orElseThrow(() -> new NotFoundException("GIS server not found."));
    }

    @Transactional(readOnly = true)
    public Connection connectionFor(Long gisServerId) {
        return connectionFor(requireServer(gisServerId));
    }

    public Connection connectionFor(GisServer server) {
        if (server.getRestUrl() == null || server.getRestUrl().isBlank()) {
            throw new BadRequestException("This GIS server has no REST URL configured.");
        }
        String password = null;
        if (server.getRestPasswordEnc() != null && !server.getRestPasswordEnc().isBlank()) {
            password = crypto.decrypt(server.getRestPasswordEnc());
        }
        return new Connection(server.getRestUrl(), server.getRestUsername(), password);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> testConnection(Long gisServerId) {
        GisServer server = requireServer(gisServerId);
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            client.aboutVersion(connectionFor(server));
            result.put("ok", true);
            result.put("message", "Connected to GeoServer REST.");
        } catch (GeoServerException ex) {
            result.put("ok", false);
            result.put("status", ex.getStatus());
            result.put("message", ex.getMessage());
        }
        return result;
    }
}
