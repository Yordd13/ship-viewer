// AisPosition: one AIS report, flagging whether its timestamp had fractional seconds to resolve duplicates.

package com.wisertech.shipviewer.ais.model;

import java.time.Instant;

public record AisPosition(
        String mmsi,
        Instant timestamp,
        boolean subSecond,
        double latitude,
        double longitude,
        Double sog,
        Double cog,
        String vesselName) {
}
