// RunLog: the pass start time and raw CFAR candidate count stored for a run.

package com.wisertech.shipviewer.run.model;

import java.time.Instant;

public record RunLog(Instant passStart, Integer cfarRawCount) {
}
