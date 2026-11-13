package org.orekit.files.sp3;

import java.io.StringWriter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.orekit.Utils;
import org.orekit.errors.OrekitException;
import org.orekit.frames.FramesFactory;
import org.orekit.data.DataSource;
import org.orekit.gnss.PredefinedTimeSystem;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.CartesianDerivativesFilter;

/** Temporary research-only reproducibility check. Not proposed as an upstream patch. */
class SP3EpochMergeResearchTest {

    @BeforeEach
    void setup() {
        Utils.setDataRoot("regular-data");
    }

    private SP3 create(final double secondDateOffset) {
        final AbsoluteDate t0 = AbsoluteDate.J2000_EPOCH;
        final SP3Header h = new SP3Header();
        h.setVersion('d');
        h.setFilter(CartesianDerivativesFilter.USE_P);
        h.setType(SP3FileType.GPS);
        h.setTimeSystem(PredefinedTimeSystem.GPS);
        h.setDataUsed(List.of(DataUsed.ORBIT));
        h.setEpoch(t0);
        h.setGpsWeek(1042);
        h.setSecondsOfWeek(0.0);
        h.setModifiedJulianDay(51544);
        h.setDayFraction(0.5);
        h.setEpochInterval(900);
        h.setNumberOfEpochs(2);
        h.setCoordinateSystem("IGS20");
        h.setOrbitTypeKey("FIT");
        h.setAgency("TEST");
        h.setPosVelBase(1.25);
        h.setClockBase(1.025);

        final SP3 sp3 = new SP3(h, 3.986004418e14, 2, FramesFactory.getGCRF());
        sp3.addSatellite("G01");
        sp3.addSatellite("G02");
        h.setAccuracy(0, 0.001);
        h.setAccuracy(1, 0.001);
        sp3.getSatellites().get("G01").addCoordinate(coordinate(t0, 2.2e7), 900);
        sp3.getSatellites().get("G02").addCoordinate(coordinate(t0.shiftedBy(secondDateOffset), 2.3e7), 900);
        return sp3;
    }

    private SP3Coordinate coordinate(final AbsoluteDate date, final double x) {
        return new SP3Coordinate(date, new Vector3D(x, 0, 0), null,
                                 Vector3D.ZERO, null, 0.0, Double.NaN, 0.0, Double.NaN,
                                 false, false, false, false);
    }

    @Test
    void sameSatelliteConsecutiveCloseEpochsRemainSerializable() throws Exception {
        final SP3 input = create(0);
        final AbsoluteDate secondDate = AbsoluteDate.J2000_EPOCH.shiftedBy(0.5);
        input.getSatellites().get("G01").addCoordinate(coordinate(secondDate, 2.2e7), 900);
        input.validate(true, "same-satellite");
        final StringWriter out = new StringWriter();
        new SP3Writer(out, "same-satellite", TimeScalesFactory.getTimeScales()).write(input);
        final long emitted = out.toString().lines().filter(line -> line.startsWith("*  ")).count();
        Assertions.assertEquals(2L, emitted);
        final byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        final SP3 reparsed = new SP3Parser().parse(
            new DataSource("same-satellite", () -> new ByteArrayInputStream(bytes)));
        Assertions.assertEquals(2, reparsed.getHeader().getNumberOfEpochs());
    }

    @Test
    void distinctEpochsWithinToleranceFailBeforeOutput() throws Exception {
        final SP3 input = create(0.5);
        input.validate(true, "research");
        input.validate(false, "research"); // Public validation keeps its previous contract.
        final StringWriter out = new StringWriter();
        final OrekitException exception = Assertions.assertThrows(OrekitException.class,
            () -> new SP3Writer(out, "research", TimeScalesFactory.getTimeScales()).write(input));
        Assertions.assertEquals(org.orekit.errors.OrekitMessages.SP3_NUMBER_OF_EPOCH_MISMATCH,
                                exception.getSpecifier());
        Assertions.assertTrue(exception.getMessage().contains("found 1 epochs"));
        Assertions.assertEquals("", out.toString(), "Failure must occur before any bytes are written");
        System.out.println("RESEARCH 0.5s collision: rejected before output");
    }

    @Test
    void subFormatResolutionDistinctEpochsFailBeforeOutput() throws Exception {
        final SP3 input = create(1.0e-9);
        input.validate(true, "research-sub-resolution");
        final StringWriter out = new StringWriter();
        Assertions.assertThrows(OrekitException.class,
            () -> new SP3Writer(out, "research-sub-resolution", TimeScalesFactory.getTimeScales()).write(input));
        Assertions.assertEquals("", out.toString());
        System.out.println("RESEARCH 1ns collision: rejected before output");
    }

    @Test
    void identicalDatesAcrossSatellitesRemainOneEpoch() throws Exception {
        final SP3 input = create(0.0);
        input.getHeader().setNumberOfEpochs(1);
        input.validate(false, "research-same-epoch");
        final StringWriter out = new StringWriter();
        new SP3Writer(out, "research-same-epoch", TimeScalesFactory.getTimeScales()).write(input);
        Assertions.assertEquals(1L, out.toString().lines().filter(s -> s.startsWith("*  ")).count());
        final byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        final SP3 parsed = new SP3Parser().parse(
            new DataSource("research-same-epoch", () -> new ByteArrayInputStream(bytes)));
        Assertions.assertEquals(1, parsed.getHeader().getNumberOfEpochs());
        System.out.println("RESEARCH identical times across satellites: reparse=PASS");
    }

    @Test
    void smallDeviationNearNextGridEpochStillRoundTrips() throws Exception {
        final SP3 input = create(900.5);
        input.validate(false, "research-jitter");
        final StringWriter out = new StringWriter();
        new SP3Writer(out, "research-jitter", TimeScalesFactory.getTimeScales()).write(input);
        final byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        final SP3 parsed = new SP3Parser().parse(
            new DataSource("research-jitter", () -> new ByteArrayInputStream(bytes)));
        Assertions.assertEquals(2, parsed.getHeader().getNumberOfEpochs());
        System.out.println("RESEARCH 900.5s: reparse=PASS");
    }

    @Test
    void controlSeparatedEpochsArePreserved() throws Exception {
        final SP3 input = create(900.0);
        input.validate(false, "research-control");
        final StringWriter out = new StringWriter();
        new SP3Writer(out, "research-control", TimeScalesFactory.getTimeScales()).write(input);
        final long epochs = out.toString().lines().filter(s -> s.startsWith("*  ")).count();
        System.out.println("RESEARCH control: input=2 output=" + epochs);
        Assertions.assertEquals(2, epochs);
        final byte[] bytes = out.toString().getBytes(StandardCharsets.UTF_8);
        final SP3 parsed = new SP3Parser().parse(
            new DataSource("research-control", () -> new ByteArrayInputStream(bytes)));
        Assertions.assertEquals(2, parsed.getHeader().getNumberOfEpochs());
        System.out.println("RESEARCH control: reparse=PASS");
    }
}
