-- Fixed things that are not ships but show up as echoes or even send AIS: the
-- gas platform broadcasting as MMSI 2070910, breakwaters, buoys. Entered by
-- hand, edited, and the first table Hibernate maps (D9, D33).

CREATE TABLE known_object (
    id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name      VARCHAR(100)    NOT NULL,

    -- platform, breakwater, buoy, ...
    kind      VARCHAR(32)     NOT NULL,

    latitude  DECIMAL(8,6)    NOT NULL,
    longitude DECIMAL(9,6)    NOT NULL,

    -- Only for objects that transmit AIS.
    mmsi      VARCHAR(10)     NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_known_object_mmsi UNIQUE (mmsi),

    -- Typed in by hand, so checked here.
    CONSTRAINT ck_known_object_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT ck_known_object_longitude CHECK (longitude BETWEEN -180 AND 180)
);
