package org.orekit.files.sp3;

import java.util.Arrays;
import java.util.List;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.Utils;
import org.orekit.frames.FramesFactory;
import org.orekit.gnss.PredefinedTimeSystem;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.CartesianDerivativesFilter;

/**
 * Diagnostic benchmark: compare pre-existing parsing validation to write validation.
 * Run manually; timing is informational, not a performance assertion.
 */
class SP3ValidationPerformanceResearchTest {
    @BeforeEach
    void setup() {
        Utils.setDataRoot("regular-data");
    }

    private SP3 create(final int epochs) {
        final AbsoluteDate base = AbsoluteDate.J2000_EPOCH;
        final SP3Header header = new SP3Header();
        header.setVersion('d');
        header.setFilter(CartesianDerivativesFilter.USE_P);
        header.setType(SP3FileType.GPS);
        header.setTimeSystem(PredefinedTimeSystem.GPS);
        header.setDataUsed(List.of(DataUsed.ORBIT));
        header.setEpoch(base);
        header.setGpsWeek(1042);
        header.setSecondsOfWeek(0);
        header.setModifiedJulianDay(51544);
        header.setDayFraction(0.5);
        header.setEpochInterval(900);
        header.setNumberOfEpochs(epochs);
        header.setCoordinateSystem("IGS20");
        header.setOrbitTypeKey("FIT");
        header.setAgency("TEST");
        header.setPosVelBase(1.25);
        header.setClockBase(1.025);
        final SP3 sp3 = new SP3(header, 3.986004418e14, 2, FramesFactory.getGCRF());
        sp3.addSatellite("G01");
        sp3.addSatellite("G02");
        header.setAccuracy(0, 0.001);
        header.setAccuracy(1, 0.001);
        for (int i = 0; i < epochs; ++i) {
            final AbsoluteDate time = base.shiftedBy(900.0 * i);
            for (final String id : List.of("G01", "G02")) {
                final SP3Coordinate point = new SP3Coordinate(
                    time, new Vector3D(2.2e7, 0, 0), null,
                    Vector3D.ZERO, null, 0.0, Double.NaN, 0.0, Double.NaN,
                    false, false, false, false);
                sp3.getSatellites().get(id).addCoordinate(point, 900);
            }
        }
        return sp3;
    }

    @Test
    void compareValidationCosts() {
        for (final int size : new int[] { 10_000, 100_000 }) {
            final SP3 sp3 = create(size);
            // Alternate ordering and warm up to reduce compilation/order bias.
            for (int i = 0; i < 4; ++i) {
                sp3.validate(false, "bench");
                sp3.validate(true, "bench");
            }
            final long[] parse = new long[10];
            final long[] write = new long[10];
            for (int i = 0; i < 10; ++i) {
                if ((i & 1) == 0) {
                    parse[i] = time(sp3, true);
                    write[i] = time(sp3, false);
                } else {
                    write[i] = time(sp3, false);
                    parse[i] = time(sp3, true);
                }
            }
            Arrays.sort(parse);
            Arrays.sort(write);
            final double parseMs = (parse[4] + parse[5]) / 2.0e6;
            final double writeMs = (write[4] + write[5]) / 2.0e6;
            System.out.printf("BENCHMARK epochs=%d coordinates=%d parse_median_ms=%.3f write_median_ms=%.3f delta_ms=%.3f%n",
                              size, 2 * size, parseMs, writeMs, writeMs - parseMs);
        }
    }

    private long time(final SP3 sp3, final boolean parsing) {
        final long start = System.nanoTime();
        sp3.validate(parsing, "bench");
        return System.nanoTime() - start;
    }
}
