package com.ahmali.ufuq.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CityIndexTest {

    private val fixture = """
        UFUQ-CITIES	1	6	2026-10-19
        #tz	America/Vancouver	Asia/Baghdad	Asia/Kuwait	Europe/Oslo
        #country	Canada	Iraq	Kuwait	Norway
        Surrey	British Columbia	0	49.1066	-122.8251	89	0	Surrey City
        Karbala	Karbala	1	32.6160	44.0249	32	1	كربلاء|Kerbela|Karbela
        As Samawah	Al Muthanna	1	31.3319	45.2944	10	1	السماوة|Samawa
        Ar Rumaythiyah	Hawalli	2	29.3117	48.0743	10	2	الرميثية
        Ar Rumaythah	Al Muthanna	1	31.5283	45.2014	10	1	الرميثة|Rumaitha
        Tromsø	Troms	3	69.6492	18.9553	10	3	Tromso
    """.trimIndent()

    private fun index() = CityIndex.load(fixture.byteInputStream())

    @Test
    fun loads() {
        val idx = index()
        assertEquals(6, idx.size)
    }

    @Test
    fun forgivingSpellingFindsIraqiTowns() {
        val idx = index()
        assertEquals("Ar Rumaythah", idx.search("Rumaitha").first().name)
        assertEquals("Ar Rumaythah", idx.search("rumaythah").first().name)
        assertEquals("As Samawah", idx.search("Samawa").first().name)
        assertEquals("As Samawah", idx.search("as-samawah").first().name)
        assertEquals("Karbala", idx.search("Kerbala").first().name)
    }

    @Test
    fun arabicScriptSearch() {
        val idx = index()
        assertEquals("Karbala", idx.search("كربلاء").first().name)
        assertEquals("Ar Rumaythah", idx.search("الرميثة").first().name)
        assertEquals("Ar Rumaythah", idx.search("رميثة").first().name)
    }

    @Test
    fun accentsAreIgnored() {
        assertEquals("Tromsø", index().search("tromso").first().name)
    }

    @Test
    fun emptyQueryReturnsNothing() {
        assertTrue(index().search("  ").isEmpty())
    }

    @Test
    fun nearestCity() {
        val idx = index()
        assertEquals("Surrey", idx.nearest(49.19, -122.85)!!.name)
        assertEquals("Karbala", idx.nearest(32.6, 44.0)!!.name)
        assertEquals("Surrey, British Columbia, Canada", idx.nearest(49.19, -122.85)!!.label)
    }

    @Test
    fun normalizationEquivalences() {
        assertEquals(SearchText.normalize("Ar Rumaythah"), SearchText.normalize("Rumaitha"))
        assertEquals(SearchText.normalize("Samawah"), SearchText.normalize("Samawa"))
        assertEquals(SearchText.normalize("Qom"), SearchText.normalize("Qum"))
        assertEquals(SearchText.normalize("الرميثة"), SearchText.normalize("رميثة"))
    }
}
