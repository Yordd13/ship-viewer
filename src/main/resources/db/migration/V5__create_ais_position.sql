-- Every AIS position kept, full history (D4): the collector's JSONL store and
-- the historical per-pass ais/*.csv files, told apart by source (D17). Not
-- linked to a pass; which positions belong to one is a query on time and area.
-- About 40 000 rows a day. D33.

CREATE TABLE ais_position (
    id         BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,

    -- Text, not a number: an MMSI is an identifier, and leading zeros mean
    -- something (coast stations). 7 to 9 digits in the store so far.
    mmsi       VARCHAR(10)       NOT NULL,

    -- UTC, to the microsecond: some sources report fractions of a second.
    -- Writers truncate anything finer rather than letting MySQL round it, or a
    -- report at .9999995 would land in the next second.
    ts         DATETIME(6)       NOT NULL,

    -- The same instant cut to the whole second, for the unique key (§4.2).
    ts_second  DATETIME GENERATED ALWAYS AS (DATE_FORMAT(ts, '%Y-%m-%d %H:%i:%s')) VIRTUAL,

    latitude   DOUBLE            NOT NULL,
    longitude  DOUBLE            NOT NULL,

    -- As reported; any of them can be missing.
    sog        DECIMAL(4,1)      NULL,
    cog        DECIMAL(4,1)      NULL,
    heading    SMALLINT UNSIGNED NULL,
    nav_status TINYINT UNSIGNED  NULL,

    -- The name as broadcast, 20 characters in AIS; room to spare, because a
    -- name too long for its column would make the collector lose the row.
    name       VARCHAR(64)       NULL,

    -- Only the collector reports ship_type; only VesselAPI reported imo.
    ship_type  SMALLINT UNSIGNED NULL,
    imo        INT UNSIGNED      NULL,

    -- aiscast or vesselapi.
    source     VARCHAR(16)       NOT NULL,

    PRIMARY KEY (id),

    -- A repeated import, or a replay after an outage, cannot store a report
    -- twice (§4.2, Phase 4).
    CONSTRAINT uq_ais_position UNIQUE (mmsi, ts_second, source),

    -- The per-pass query is a time window first; the area filter is a bounding
    -- box on the rows it returns.
    INDEX ix_ais_position_ts (ts)
);
