// RunDetail: everything the map needs for one run: summary, AIS times, GeoJSON layers and raster.

package com.wisertech.shipviewer.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wisertech.shipviewer.run.model.RunSummary;
import java.time.Instant;
import java.util.Map;

public record RunDetail(
        RunSummary summary,
        Instant aisEarliestTime,
        Instant aisLatestTime,
        Map<String, Object> detections,
        Map<String, Object> ais,

        @JsonInclude(JsonInclude.Include.NON_NULL) RasterLayer raster) {
}
