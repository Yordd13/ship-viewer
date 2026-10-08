// detection: builds a test detection at a position and time, with fixed size, slice, mission and orbit.
// position: builds a test AIS report for an MMSI at a time and position, with fixed speed and course.
// properties(): builds viewer properties with no pipeline folder or Go command.
// properties(Path, String): builds viewer properties with the given pipeline folder and Go command.

package com.wisertech.shipviewer;

import com.wisertech.shipviewer.ais.model.AisPosition;
import com.wisertech.shipviewer.config.ViewerProperties;
import com.wisertech.shipviewer.detection.model.Detection;
import java.nio.file.Path;
import java.time.Instant;

public final class Fixtures {
    public static final Instant PASS_START = Instant.parse("2026-10-02T15:50:58Z");

    private Fixtures() {
    }

    public static Detection detection(String id, double latitude, double longitude, Instant acquired) {
        return new Detection(id, latitude, longitude, 40.0, 120.0, "slice_1", acquired, "S1C",
                "009708", 5000.0);
    }

    public static AisPosition position(String mmsi, Instant reported, double latitude, double longitude) {
        return new AisPosition(mmsi, reported, false, latitude, longitude, 10.5, 90.0, "VESSEL " + mmsi);
    }

    public static ViewerProperties properties() {
        return properties(null, null);
    }

    public static ViewerProperties properties(Path pipelineDir, String goCommand) {
        return new ViewerProperties(null, pipelineDir, null, 2000, goCommand, "bulgaria", 15);
    }
}
