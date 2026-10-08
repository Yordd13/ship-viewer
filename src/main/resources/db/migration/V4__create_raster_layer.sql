-- Where a pass's radar picture is and where it belongs on the map: the
-- .raster.json sidecar. The tiles themselves stay on disk. At most one per
-- pass. D33.

CREATE TABLE raster_layer (
    id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    pass_run_id     BIGINT UNSIGNED  NOT NULL,

    -- "tiles" for every pass so far; "image" for a single picture.
    type            VARCHAR(16)      NOT NULL,

    -- 2026-09-26T155130Z_004751/{z}/{x}/{y}.webp, relative to the tiles folder.
    file            VARCHAR(255)     NOT NULL,

    bound_south     DOUBLE           NOT NULL,
    bound_west      DOUBLE           NOT NULL,
    bound_north     DOUBLE           NOT NULL,
    bound_east      DOUBLE           NOT NULL,

    -- Only for tiles.
    max_native_zoom TINYINT UNSIGNED NULL,
    max_zoom        TINYINT UNSIGNED NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_raster_layer_pass UNIQUE (pass_run_id),
    CONSTRAINT fk_raster_layer_pass_run FOREIGN KEY (pass_run_id) REFERENCES pass_run (id)
);
