// aPointIsNoDistanceFromItself: checks that the distance from a point to itself is zero.
// oneDegreeOfLatitudeIsAbout111Kilometres: checks that one degree of latitude is about 111,195 metres.
// aDegreeOfLongitudeShrinksWithLatitude: checks a degree of longitude at Varna is the equator's times cos(43.2).
// itIsSymmetric: checks that swapping the two points gives the same distance.

package com.wisertech.shipviewer.match.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class HaversineTest {
    @Test
    void aPointIsNoDistanceFromItself() {
        assertThat(Haversine.metresBetween(43.2, 28.0, 43.2, 28.0)).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Kilometres() {
        assertThat(Haversine.metresBetween(43.0, 28.0, 44.0, 28.0)).isCloseTo(111_195, within(1.0));
    }

    @Test
    void aDegreeOfLongitudeShrinksWithLatitude() {
        double atEquator = Haversine.metresBetween(0, 28.0, 0, 29.0);
        double atVarna = Haversine.metresBetween(43.2, 28.0, 43.2, 29.0);

        assertThat(atVarna).isCloseTo(atEquator * Math.cos(Math.toRadians(43.2)), within(50.0));
    }

    @Test
    void itIsSymmetric() {
        assertThat(Haversine.metresBetween(43.1, 28.1, 42.9, 29.3))
                .isEqualTo(Haversine.metresBetween(42.9, 29.3, 43.1, 28.1));
    }
}
