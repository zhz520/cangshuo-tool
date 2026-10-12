package com.cangshuo.toolbox.feature.lightmeter

import com.cangshuo.toolbox.feature.lightmeter.data.SensorLightMeterRepository
import com.cangshuo.toolbox.feature.lightmeter.domain.*
import com.cangshuo.toolbox.feature.sensors.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class LightMeterTest {
    private fun sample(lux: Double, seconds: Long = 1) = LightSample(lux, seconds * 1_000_000_000L, 3)

    @Test fun footCandlesUseInternationalFootSquared() {
        assertEquals(1.0, LightUnit.FOOT_CANDLE.convert(10.763910416709722), 1e-12)
        assertEquals(0.0, LightUnit.FOOT_CANDLE.convert(0.0), 0.0)
        assertEquals(123.4, LightUnit.LUX.convert(123.4), 0.0)
    }
    @Test fun rawStatisticsAreStableAndZeroIsValid() {
        val session = LightSession().add(sample(0.0)).add(sample(300.0, 2)).add(sample(600.0, 3))
        assertEquals(3L, session.count)
        assertEquals(0.0, session.minimum!!, 0.0)
        assertEquals(600.0, session.maximum!!, 0.0)
        assertEquals(300.0, session.mean, 1e-12)
        assertEquals(450.0, session.mean * 1.5, 1e-12)
        assertEquals(300.0, session.mean, 1e-12)
    }
    @Test fun invalidAndOutOfOrderSamplesNeverEnterStatistics() {
        val original = LightSession().add(sample(100.0, 2))
        for (lux in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, LightPolicy.MAX_LUX + 1)) {
            assertSame(original, original.add(sample(lux, 3)))
        }
        assertSame(original, original.add(sample(50.0, 1)))
        assertSame(original, original.add(sample(50.0, 2)))
        assertSame(original, original.add(LightSample(50.0, 0, 3)))
    }
    @Test fun historyIsBoundedWhileStatisticsCoverTheWholeSession() {
        var session = LightSession()
        for (index in 1L..1_000L) session = session.add(sample(index.toDouble(), index))
        assertEquals(1_000L, session.count)
        assertEquals(600, session.history.size)
        assertEquals(401.0, session.history.first().lux, 0.0)
        assertEquals(500.5, session.mean, 1e-12)
        assertEquals(1.0, session.minimum!!, 0.0)
    }
    @Test fun windowsUseElapsedTimeRatherThanSampleIndices() {
        val session = LightSession().add(sample(1.0, 1)).add(sample(2.0, 11)).add(sample(3.0, 16)).add(sample(4.0, 31))
        assertEquals(listOf(3.0, 4.0), session.window(15).map { it.lux })
        assertEquals(4, session.window(30).size)
    }
    @Test fun referenceAcceptsDecimalCommaAndRangeEndpoints() {
        assertEquals(LightCalibration.Applied(1.5), LightPolicy.calibrate("150,0", sample(100.0)))
        assertEquals(LightCalibration.Applied(0.1), LightPolicy.calibrate("10", sample(100.0)))
        assertEquals(LightCalibration.Applied(10.0), LightPolicy.calibrate("1000", sample(100.0)))
    }
    @Test fun malformedCalibrationNeverSilentlyChangesAValue() {
        for (text in listOf("", "-10", "0", "NaN", "1e3", "1.23456", "1,234.0", "1000000001", "9".repeat(17))) {
            assertEquals(LightCalibration.Rejected(LightCalibrationError.INVALID_REFERENCE), LightPolicy.calibrate(text, sample(100.0)))
        }
    }
    @Test fun calibrationRejectsZeroUnavailableAndExtremeRatios() {
        assertEquals(LightCalibration.Rejected(LightCalibrationError.NO_READING), LightPolicy.calibrate("100", null))
        assertEquals(LightCalibration.Rejected(LightCalibrationError.NO_READING), LightPolicy.calibrate("100", sample(0.0)))
        for (text in listOf("9", "1001")) {
            assertEquals(LightCalibration.Rejected(LightCalibrationError.FACTOR_OUT_OF_RANGE), LightPolicy.calibrate(text, sample(100.0)))
        }
    }
    @Test fun csvKeepsRawAndCorrectedColumnsAndRelativeTime() {
        assertEquals("elapsed_s,raw_lux,corrected_lux\n0.0,100.0,150.0\n3.0,200.0,300.0\n",
            LightPolicy.csv(listOf(sample(100.0), sample(200.0, 4)), 1.5))
        assertEquals("elapsed_s,raw_lux,corrected_lux\n", LightPolicy.csv(emptyList(), 1.0))
    }
    @Test fun csvHasTheSameMemoryBudgetAsTheChart() {
        val csv = LightPolicy.csv((1L..800L).map { sample(it.toDouble(), it) }, 1.0)
        assertEquals(601, csv.lines().count { it.isNotEmpty() })
        assertTrue(csv.lines()[1].startsWith("0.0,201.0,"))
    }
    @Test fun environmentThresholdsUseTheCorrectedLuxScale() {
        assertEquals(LightBand.DARK, LightPolicy.band(0.0))
        assertEquals(LightBand.DIM, LightPolicy.band(10.0))
        assertEquals(LightBand.INDOOR, LightPolicy.band(100.0))
        assertEquals(LightBand.BRIGHT, LightPolicy.band(500.0))
        assertEquals(LightBand.VERY_BRIGHT, LightPolicy.band(10_000.0))
    }
    @Test fun sensorAdapterSelectsLightAndRejectsMalformedReadings() = runTest {
        val descriptor = SensorDescriptor(9, "Light", "Vendor", 5, 1, 40_000f, 1f, 0.1f, 0, false, true)
        val sensors = object : SensorsRepository {
            override suspend fun catalog() = SensorCatalog(listOf(descriptor.copy(id = 1, type = 1), descriptor), false)
            override fun observe(id: Int): Flow<SensorReading> {
                assertEquals(9, id)
                return flowOf(SensorReading(listOf(100f), 3, 1, false), SensorReading(listOf(null), 0, 2, false),
                    SensorReading(listOf(-1f), 0, 3, false), SensorReading(emptyList(), 0, 4, false))
            }
        }
        val events = SensorLightMeterRepository(sensors).observe().toList()
        assertEquals(LightEvent.Source(descriptor), events[0])
        assertEquals(LightEvent.Reading(LightSample(100.0, 1, 3)), events[1])
        assertEquals(List(3) { LightEvent.InvalidReading }, events.drop(2))
    }
    @Test fun missingLightSensorIsDistinctFromRequestFailure() = runTest {
        val sensors = object : SensorsRepository {
            override suspend fun catalog() = SensorCatalog(emptyList(), false)
            override fun observe(id: Int): Flow<SensorReading> = error("Must not subscribe")
        }
        try { SensorLightMeterRepository(sensors).observe().toList(); fail("Unsupported sensor must fail") }
        catch (_: LightSensorUnavailable) { /* Expected. */ }
    }
}
