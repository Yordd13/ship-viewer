// ViewerProperties: the shipviewer settings: AOI file, pipeline and tile folders, Go command, area and match limits.

package com.wisertech.shipviewer.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("shipviewer")
public record ViewerProperties(
        Path aoiFile,
        Path pipelineDir,
        Path tilesDir,
        @DefaultValue("2000") double matchRadiusMeters,
        @DefaultValue("go") String goCommand,
        @DefaultValue("bulgaria") String area,
        @DefaultValue("15") double aisWindowMinutes) {
}
