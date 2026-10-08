// detailOf: turns a run into the map's detail payload with summary, timing, features and raster layer.
// detectionFeatures: builds the GeoJSON points of the detections with their match results.
// aisFeatures: builds the GeoJSON points of the AIS positions, flagging vessels a detection claimed.

package com.wisertech.shipviewer.web.mapper;

import com.wisertech.shipviewer.ais.model.AisPosition;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.detection.model.MatchedDetection;
import com.wisertech.shipviewer.run.model.Run;
import com.wisertech.shipviewer.web.dto.RasterLayer;
import com.wisertech.shipviewer.web.dto.RunDetail;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RunPayloads {
    private RunPayloads() {
    }

    public static RunDetail detailOf(Run run) {
        return new RunDetail(
                run.summary(),
                run.timing().earliest(),
                run.timing().latest(),
                detectionFeatures(run),
                aisFeatures(run),
                RasterLayer.of(run.raster()));
    }

    private static Map<String, Object> detectionFeatures(Run run) {
        List<Map<String, Object>> features = new ArrayList<>(run.matched().size());
        for (MatchedDetection candidate : run.matched()) {
            Detection detection = candidate.detection();
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("detectionId", detection.detectionId());
            properties.put("latitude", detection.latitude());
            properties.put("longitude", detection.longitude());
            properties.put("widthM", detection.widthM());
            properties.put("lengthM", detection.lengthM());
            properties.put("sourceSlice", detection.sourceSlice());
            properties.put("acquisitionTime", detection.acquisitionTime());
            properties.put("distanceToShoreM", detection.distanceToShoreM());
            properties.put("matched", candidate.matched());
            properties.put("matchedMmsi", candidate.matchedMmsi());
            properties.put("distanceMeters", candidate.distanceMeters());
            features.add(GeoJson.point(detection.latitude(), detection.longitude(), properties));
        }
        return GeoJson.featureCollection(features);
    }

    private static Map<String, Object> aisFeatures(Run run) {
        Set<String> claimed = run.claimedMmsis();

        List<Map<String, Object>> features = new ArrayList<>(run.aisPositions().size());
        for (AisPosition position : run.aisPositions()) {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("mmsi", position.mmsi());
            properties.put("timestamp", position.timestamp());
            properties.put("latitude", position.latitude());
            properties.put("longitude", position.longitude());
            properties.put("sog", position.sog());
            properties.put("cog", position.cog());
            properties.put("vesselName", position.vesselName());
            properties.put("matched", claimed.contains(position.mmsi()));
            features.add(GeoJson.point(position.latitude(), position.longitude(), properties));
        }
        return GeoJson.featureCollection(features);
    }
}
