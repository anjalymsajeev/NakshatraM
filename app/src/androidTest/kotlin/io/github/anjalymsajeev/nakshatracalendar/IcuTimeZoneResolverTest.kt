package io.github.anjalymsajeev.nakshatracalendar

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.anjalymsajeev.nakshatracalendar.integration.geocoding.IcuTimeZoneResolver
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IcuTimeZoneResolverTest {
    private val resolver = IcuTimeZoneResolver()

    @Test
    fun singleAndMultiZoneCountries() {
        assertEquals(listOf(ZoneId.of("Asia/Kolkata")), resolver.zonesFor("IN"))
        assertEquals(listOf(ZoneId.of("Asia/Dubai")), resolver.zonesFor("ae"))
        val us = resolver.zonesFor("US")
        assertTrue(us.size > 5)
        assertTrue(ZoneId.of("America/New_York") in us)
    }
}
