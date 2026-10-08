// matched: tells whether an AIS transponder was matched to the detection.

package com.wisertech.shipviewer.detection.model;

public record MatchedDetection(
        Detection detection,
        String matchedMmsi,
        Double distanceMeters) {
    public boolean matched() {
        return matchedMmsi != null;
    }
}
