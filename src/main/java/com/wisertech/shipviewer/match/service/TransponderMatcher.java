// match: pairs detections with AIS vessels one-to-one, nearest first, within the configured radius.
// pairsWithinRadius: lists every detection and AIS position pair no farther apart than the radius.

package com.wisertech.shipviewer.match.service;

import com.wisertech.shipviewer.ais.model.AisPosition;
import com.wisertech.shipviewer.config.ViewerProperties;
import com.wisertech.shipviewer.detection.model.Detection;
import com.wisertech.shipviewer.detection.model.MatchedDetection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TransponderMatcher {
    private final ViewerProperties properties;

    public TransponderMatcher(ViewerProperties properties) {
        this.properties = properties;
    }

    public List<MatchedDetection> match(List<Detection> detections, List<AisPosition> positions) {
        List<Pair> pairs =
                pairsWithinRadius(detections, positions, properties.matchRadiusMeters());
        pairs.sort(Comparator.comparingDouble(Pair::metres));

        String[] claimedMmsi = new String[detections.size()];
        Double[] claimedMetres = new Double[detections.size()];
        Set<String> vesselsTaken = new HashSet<>();

        for (Pair pair : pairs) {
            String mmsi = positions.get(pair.positionIndex()).mmsi();
            if (claimedMmsi[pair.detectionIndex()] != null || vesselsTaken.contains(mmsi)) {
                continue;
            }
            claimedMmsi[pair.detectionIndex()] = mmsi;
            claimedMetres[pair.detectionIndex()] = Math.round(pair.metres() * 10.0) / 10.0;
            vesselsTaken.add(mmsi);
        }

        List<MatchedDetection> matched = new ArrayList<>(detections.size());
        for (int index = 0; index < detections.size(); index++) {
            matched.add(new MatchedDetection(
                    detections.get(index), claimedMmsi[index], claimedMetres[index]));
        }
        return matched;
    }

    private static List<Pair> pairsWithinRadius(
            List<Detection> detections, List<AisPosition> positions, double radiusMetres) {
        List<Pair> pairs = new ArrayList<>();
        for (int d = 0; d < detections.size(); d++) {
            Detection detection = detections.get(d);
            for (int a = 0; a < positions.size(); a++) {
                AisPosition position = positions.get(a);
                double metres = Haversine.metresBetween(
                        detection.latitude(), detection.longitude(),
                        position.latitude(), position.longitude());
                if (metres <= radiusMetres) {
                    pairs.add(new Pair(d, a, metres));
                }
            }
        }
        return pairs;
    }

    private record Pair(int detectionIndex, int positionIndex, double metres) {
    }
}
