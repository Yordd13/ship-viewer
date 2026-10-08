// RasterSource: the tile pyramid stored for a run, with its bounds and zoom limits.

package com.wisertech.shipviewer.raster.model;

public record RasterSource(
        String type,
        String file,
        GeoBounds bounds,
        Integer maxNativeZoom,
        Integer maxZoom) {
}
