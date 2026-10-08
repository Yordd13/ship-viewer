// asLeafletCorners: returns the box as Leaflet's corner pair, south-west first then north-east.

package com.wisertech.shipviewer.raster.model;

import java.util.List;

public record GeoBounds(double south, double west, double north, double east) {
    public List<List<Double>> asLeafletCorners() {
        return List.of(List.of(south, west), List.of(north, east));
    }
}
