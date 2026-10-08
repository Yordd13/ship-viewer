-- One row per pass: one pipeline run over one area. Mirrors the key=value .log
-- file the pipeline writes beside each detections CSV (writeRunLog in
-- merge_detections.go); nothing here is invented. See docs/mysql-migration.md in
-- the pipeline repository, D30.
--
-- Once applied, this file is never edited: Flyway keeps its checksum and refuses
-- to start if it changes. A correction is a new migration.

CREATE TABLE pass_run (
    -- A plain number, so pass_slice, detection and raster_layer can point at
    -- a pass with one column. The pass's real identity is the unique key below.
    id                      BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,

    -- UTC, as every time in this project. DATETIME stores the value as given;
    -- TIMESTAMP would convert through the session's zone and ends in 2038.
    pass_start              DATETIME          NOT NULL,

    -- Printed with leading zeros (004751) in product names; a number all the
    -- same. Restarts per satellite, so it does not identify a pass on its own.
    absolute_orbit          INT UNSIGNED      NOT NULL,

    area                    VARCHAR(32)       NOT NULL,

    -- Share of the area the pass covered. The pipeline writes nothing when it
    -- could not tell, hence NULL.
    coverage_pct            TINYINT UNSIGNED  NULL,

    slices_processed        SMALLINT UNSIGNED NOT NULL,
    slices_failed           SMALLINT UNSIGNED NOT NULL,

    -- What CFAR found, and where each of those went. These describe what the run
    -- did, which only re-running SNAP could change; unlike transponder matches
    -- they are not a view of a setting (principle 3).
    cfar_detections_raw     INT UNSIGNED      NOT NULL,
    dropped_outside_aoi     INT UNSIGNED      NOT NULL,
    dropped_seam_duplicates INT UNSIGNED      NOT NULL,

    -- What the pipeline says it wrote. Should equal this pass's rows in
    -- detection; kept precisely so that a lost row shows as a mismatch.
    detections_written      INT UNSIGNED      NOT NULL,

    aoi_vertices            SMALLINT UNSIGNED NOT NULL,

    PRIMARY KEY (id),

    -- One run per pass and area (D16). A second import of the same pass fails
    -- rather than making a duplicate.
    CONSTRAINT uq_pass_run_area_start UNIQUE (area, pass_start),

    CONSTRAINT ck_pass_run_coverage CHECK (coverage_pct BETWEEN 0 AND 100),

    -- Every raw detection is either outside the area, a seam duplicate, or
    -- written. merge_detections.go computes them that way, and all eight
    -- existing logs agree.
    CONSTRAINT ck_pass_run_counts_add_up CHECK (
        cfar_detections_raw = dropped_outside_aoi + dropped_seam_duplicates + detections_written
    )
);
