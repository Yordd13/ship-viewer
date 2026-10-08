-- One row per detection in the merged CSV, without the columns the CSV repeats
-- per pass or per slice (those are in pass_run and pass_slice). No transponder
-- match: that is computed on read (principle 3). D33.

CREATE TABLE detection (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    pass_slice_id       BIGINT UNSIGNED NOT NULL,

    -- slice_1_target_013
    detection_id        VARCHAR(32)     NOT NULL,

    -- DECIMAL with the CSV's six places, so a value compares exactly with the
    -- file it came from (Phase 3's parity check).
    latitude            DECIMAL(8,6)    NOT NULL,
    longitude           DECIMAL(9,6)    NOT NULL,

    width_m             DECIMAL(6,1)    NOT NULL,
    length_m            DECIMAL(6,1)    NOT NULL,
    pixel_x             INT UNSIGNED    NOT NULL,
    pixel_y             INT UNSIGNED    NOT NULL,
    distance_to_shore_m INT UNSIGNED    NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_detection_id UNIQUE (pass_slice_id, detection_id),
    CONSTRAINT fk_detection_pass_slice FOREIGN KEY (pass_slice_id) REFERENCES pass_slice (id)
);
