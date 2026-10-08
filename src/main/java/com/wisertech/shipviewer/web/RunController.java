// list: returns the summaries of all runs, newest first.
// one: returns one run's full detail with GeoJSON layers, or throws RunNotFoundException.

package com.wisertech.shipviewer.web;

import com.wisertech.shipviewer.exception.RunNotFoundException;
import com.wisertech.shipviewer.run.model.Run;
import com.wisertech.shipviewer.run.model.RunSummary;
import com.wisertech.shipviewer.run.service.RunService;
import com.wisertech.shipviewer.web.dto.RunDetail;
import com.wisertech.shipviewer.web.mapper.RunPayloads;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs")
public class RunController {
    private final RunService runs;

    public RunController(RunService runs) {
        this.runs = runs;
    }

    @GetMapping
    public List<RunSummary> list() throws IOException {
        return runs.listRuns();
    }

    @GetMapping("/{id}")
    public RunDetail one(@PathVariable String id) throws IOException {
        Run run = runs.findRun(id);
        if (run == null) {
            throw new RunNotFoundException("No run called " + id + ".");
        }
        return RunPayloads.detailOf(run);
    }
}
