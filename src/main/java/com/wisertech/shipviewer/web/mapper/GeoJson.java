// featureCollection: wraps a list of features in a GeoJSON FeatureCollection.
// point: builds a GeoJSON point feature, longitude first, with the given properties.

package com.wisertech.shipviewer.web.mapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GeoJson {
    private GeoJson() {
    }

    public static Map<String, Object> featureCollection(List<Map<String, Object>> features) {
        Map<String, Object> collection = new LinkedHashMap<>();
        collection.put("type", "FeatureCollection");
        collection.put("features", features);
        return collection;
    }

    public static Map<String, Object> point(
            double latitude, double longitude, Map<String, Object> properties) {
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "Point");
        geometry.put("coordinates", List.of(longitude, latitude));

        Map<String, Object> feature = new LinkedHashMap<>();
        feature.put("type", "Feature");
        feature.put("geometry", geometry);
        feature.put("properties", properties);
        return feature;
    }
}
