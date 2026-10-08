// status: returns the AIS collector's current status.
// start: lets an admin start the AIS collector, answering 202 with its status.

package com.wisertech.shipviewer.web;

import com.wisertech.shipviewer.collector.model.CollectorStatus;
import com.wisertech.shipviewer.collector.service.CollectorRunner;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/collector")
public class CollectorController {
    private final CollectorRunner collector;

    public CollectorController(CollectorRunner collector) {
        this.collector = collector;
    }

    @GetMapping
    public CollectorStatus status() {
        return collector.status();
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<CollectorStatus> start() throws IOException {
        return ResponseEntity.accepted().body(collector.start());
    }
}
