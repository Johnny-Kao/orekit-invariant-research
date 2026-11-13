package org.orekit.files.sp3;

import java.util.List;
import java.util.Arrays;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.orekit.Utils;
import org.orekit.frames.FramesFactory;
import org.orekit.gnss.PredefinedTimeSystem;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.CartesianDerivativesFilter;

/** Research-only timing test. No pass/fail runtime thresholds. */
class SP3WriterPerformanceResearchTest {
    @BeforeEach
    void setup() {
        Utils.setDataRoot("regular-data");
    }

    private static class NullAppendable implements Appendable {
        public Appendable append(final CharSequence csq) { return this; }
        public Appendable append(final CharSequence csq, final int start, final int end) { return this; }
        public Appendable append(final char c) { return this; }
    }

    private SP3 fixture(final int epochs, final int satellites) {
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
        for (int j = 0; j < satellites; j++) {
            final String id = String.format("G%02d", j + 1);
            sp3.addSatellite(id);
        }
        for (int j = 0; j < satellites; j++) {
            header.setAccuracy(j, 0.001);
        }
        for (int i = 0; i < epochs; i++) {
            final AbsoluteDate time = base.shiftedBy(900.0 * i);
            for (final String id : sp3.getSatellites().keySet()) {
                final SP3Coordinate coordinate = new SP3Coordinate(
                    time, new Vector3D(2.2e7, 0, 0), null,
                    Vector3D.ZERO, null, 0.0, Double.NaN, 0.0, Double.NaN,
                    false, false, false, false);
                sp3.getSatellites().get(id).addCoordinate(coordinate, 900);
            }
        }
        return sp3;
    }

    @Test
    void benchmarkWriter() throws Exception {
        for (final int[] shape : new int[][] {{1000, 2}, {2000, 20}}) {
            final SP3 source = fixture(shape[0], shape[1]);
            final SP3Writer writer = new SP3Writer(new NullAppendable(), "bench", TimeScalesFactory.getTimeScales());
            for (int j = 0; j < 3; j++) {
                writer.write(source);
            }
            final double[] timings = new double[7];
            for (int j = 0; j < timings.length; j++) {
                final long start = System.nanoTime();
                writer.write(source);
                timings[j] = (System.nanoTime() - start) / 1.0e6;
            }
            Arrays.sort(timings);
            System.out.printf("WRITER_BENCH epochs=%d satellites=%d coordinates=%d median_ms=%.3f%n",
                              shape[0], shape[1], shape[0] * shape[1], timings[3]);
        }
    }
}
