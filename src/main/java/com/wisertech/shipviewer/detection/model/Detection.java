// Detection: one radar detection's position, size, slice, acquisition time, orbit and distance to shore.

package com.wisertech.shipviewer.detection.model;

import java.time.Instant;

public record Detection(
        String detectionId,
        double latitude,
        double longitude,
        Double widthM,
        Double lengthM,
        String sourceSlice,
        Instant acquisitionTime,
        String mission,
        String absOrbit,

        Double distanceToShoreM) {
}
