// current: returns a snapshot of the current or last job.
// search: lets an admin start a search-only pipeline job for the configured area.
// pipeline: lets an admin start a full pipeline run for the configured area.
// begin: starts a job of the given kind and answers 202 with its snapshot.

package com.wisertech.shipviewer.web;

import com.wisertech.shipviewer.config.ViewerProperties;
import com.wisertech.shipviewer.job.model.JobSnapshot;
import com.wisertech.shipviewer.job.service.JobRunner;
import java.io.IOException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {
    private final JobRunner runner;
    private final ViewerProperties properties;

    public JobController(JobRunner runner, ViewerProperties properties) {
        this.runner = runner;
        this.properties = properties;
    }

    @GetMapping
    public JobSnapshot current() {
        return runner.snapshot();
    }

    @PostMapping("/search")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<JobSnapshot> search() throws IOException {
        return begin("search",
                List.of("run", "./cmd/pipeline", "-search-only", "-area", properties.area()));
    }

    @PostMapping("/pipeline")
    @PreAuthorize("hasRole('admin')")
    public ResponseEntity<JobSnapshot> pipeline() throws IOException {
        return begin("pipeline", List.of("run", "./cmd/pipeline", "-area", properties.area()));
    }

    private ResponseEntity<JobSnapshot> begin(String kind, List<String> arguments)
            throws IOException {
        runner.start(kind, arguments);
        return ResponseEntity.accepted().body(runner.snapshot());
    }
}
