// format: writes a moment as the UTC stamp a run id starts with, such as 2026-09-07T155920Z.
// parseOrNull: reads the stamp before the first underscore of a run id, or returns null if it is not one.

package com.wisertech.shipviewer.run.model;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class FilenameStamp {
    private static final DateTimeFormatter LAYOUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private FilenameStamp() {
    }

    public static String format(Instant moment) {
        return LAYOUT.format(moment);
    }

    public static Instant parseOrNull(String runId) {
        int separator = runId.indexOf('_');
        String stamp = separator < 0 ? runId : runId.substring(0, separator);
        try {
            return LAYOUT.parse(stamp, Instant::from);
        } catch (RuntimeException notAStamp) {
            return null;
        }
    }
}
