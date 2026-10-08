// aoi: serves the area-of-interest GeoJSON file, or 404 when it is not configured or missing.

package com.wisertech.shipviewer.web;

import com.wisertech.shipviewer.config.ViewerProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AoiController {
    private final ViewerProperties properties;

    public AoiController(ViewerProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/api/aoi")
    public ResponseEntity<String> aoi() throws IOException {
        Path file = properties.aoiFile();
        if (file == null || !Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Files.readString(file));
    }
}
