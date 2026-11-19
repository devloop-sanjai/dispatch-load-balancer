package com.freightfox.dispatch.optimizer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HaversineDistanceCalculatorTest {

    private final HaversineDistanceCalculator calculator = new HaversineDistanceCalculator();

    @Test
    void samePointIsZero() {
        assertThat(calculator.distanceKm(28.6139, 77.2090, 28.6139, 77.2090)).isZero();
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertThat(calculator.distanceKm(0, 0, 1, 0)).isCloseTo(111.195, within(0.01));
    }

    @Test
    void isSymmetric() {
        double ab = calculator.distanceKm(12.9716, 77.5946, 13.0827, 80.2707);
        double ba = calculator.distanceKm(13.0827, 80.2707, 12.9716, 77.5946);
        assertThat(ab).isCloseTo(ba, within(1e-9));
    }

    @Test
    void antipodalPointsAreHalfTheEarthCircumference() {
        assertThat(calculator.distanceKm(0, 0, 0, 180)).isCloseTo(Math.PI * 6371.0, within(0.5));
    }

    @Test
    void bangaloreToChennaiIsRoughly290Km() {
        assertThat(calculator.distanceKm(12.9716, 77.5946, 13.0827, 80.2707)).isBetween(280.0, 300.0);
    }

    @Test
    void delhiToNoidaSector18IsRoughly17Km() {
        assertThat(calculator.distanceKm(28.6139, 77.2090, 28.5355, 77.3910)).isBetween(15.0, 20.0);
    }
}
