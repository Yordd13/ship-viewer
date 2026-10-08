-- A detection's distance to shore can be unknown: when the detection graph masks
-- no coastline, or the coastline file cannot be read, the pipeline measures
-- nothing and writes an empty field rather than a number (formatShoreDistance in
-- shoreline.go). V3 made the column NOT NULL because all 381 existing
-- detections have one; the first such run would then fail to reach the
-- database. D37.

ALTER TABLE detection MODIFY distance_to_shore_m INT UNSIGNED NULL;
