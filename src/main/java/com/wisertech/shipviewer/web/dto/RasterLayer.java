// of: turns a raster source into a map layer with a tiles URL and Leaflet bounds, or null.

package com.wisertech.shipviewer.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wisertech.shipviewer.raster.model.RasterSource;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RasterLayer(
        String type,
        String url,
        Integer maxNativeZoom,
        Integer maxZoom,
        List<List<Double>> bounds) {
    public static final String TILES_PREFIX = "/tiles/";

    public static RasterLayer of(RasterSource source) {
        if (source == null) {
            return null;
        }
        return new RasterLayer(
                source.type(),
                TILES_PREFIX + source.file().replace('\\', '/'),
                source.maxNativeZoom(),
                source.maxZoom(),
                source.bounds().asLeafletCorners());
    }
}
