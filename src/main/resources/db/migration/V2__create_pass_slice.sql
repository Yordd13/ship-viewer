-- One row per Sentinel-1 product in a pass: the slice_N lines of the run log,
-- plus what the detections CSV repeats on every row (product id, mission,
-- acquisition time), stored here once. D33.

CREATE TABLE pass_slice (
    id                BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    pass_run_id       BIGINT UNSIGNED  NOT NULL,

    -- The N in slice_N.
    slice_index       TINYINT UNSIGNED NOT NULL,

    -- S1D_IW_GRDH_1SDV_20260926T155130_20260926T155155_004751_008E53_DDA3
    product_name      VARCHAR(100)     NOT NULL,

    -- The catalogue's UUID. Only in the CSV, so unknown for a slice that found
    -- nothing.
    product_id        CHAR(36)         NULL,

    -- S1C or S1D.
    mission           CHAR(3)          NOT NULL,

    -- The slice's start, UTC. Every detection's acquisition_time in the CSV is
    -- exactly this.
    acquisition_start DATETIME         NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_pass_slice_index UNIQUE (pass_run_id, slice_index),

    -- No ON DELETE CASCADE: a pass with slices cannot be deleted by accident.
    CONSTRAINT fk_pass_slice_pass_run FOREIGN KEY (pass_run_id) REFERENCES pass_run (id)
);
