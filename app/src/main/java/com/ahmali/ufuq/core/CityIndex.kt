package com.ahmali.ufuq.core

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.Locale
import kotlin.math.cos

data class City(
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val elevation: Int,
    val zoneId: String
) {
    /** "City, Region, Country", skipping empty or repeated parts. */
    val label: String
        get() = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(", ")
}

/**
 * The bundled GeoNames city list (see tools/build_cities.py for the format).
 * Loading is fast; search keys are normalised lazily on first search.
 */
class CityIndex private constructor(
    private val cities: List<City>,
    private val altNames: List<List<String>>
) {
    val size: Int get() = cities.size

    @Volatile
    private var keys: Array<Array<String>>? = null

    /** Nearest city to a point (equirectangular distance; fine at city scale). */
    fun nearest(latitude: Double, longitude: Double): City? {
        var best: City? = null
        var bestD = Double.MAX_VALUE
        val k = cos(Math.toRadians(latitude))
        for (c in cities) {
            val dLat = c.latitude - latitude
            var dLon = c.longitude - longitude
            if (dLon > 180) dLon -= 360 else if (dLon < -180) dLon += 360
            val d = dLat * dLat + (dLon * k) * (dLon * k)
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
        return best
    }

    /**
     * Forgiving search over names and alternate names (including Arabic script).
     * Ignores case, accents, the Arabic article and common spelling variants.
     */
    fun search(query: String, limit: Int = 40): List<City> {
        val q = SearchText.normalize(query)
        if (q.isEmpty()) return emptyList()
        val all = keys ?: buildKeys().also { keys = it }
        val scored = ArrayList<Pair<Int, Int>>() // score, index
        for (i in cities.indices) {
            var best = Int.MAX_VALUE
            for (key in all[i]) {
                val s = when {
                    key == q -> 0
                    key.startsWith(q) -> 1
                    key.contains(" $q") -> 2
                    q.length >= 3 && key.contains(q) -> 3
                    else -> Int.MAX_VALUE
                }
                if (s < best) best = s
                if (best == 0) break
            }
            if (best != Int.MAX_VALUE) scored += best to i
        }
        // Stable sort: ties keep population order from the file.
        return scored.sortedBy { it.first }.take(limit).map { cities[it.second] }
    }

    /** Normalise all search keys now (call off the main thread to make the first search instant). */
    fun warmUp() {
        if (keys == null) keys = buildKeys()
    }

    private fun buildKeys(): Array<Array<String>> = Array(cities.size) { i ->
        val c = cities[i]
        (listOf(c.name) + altNames[i]).map { SearchText.normalize(it) }.filter { it.isNotEmpty() }
            .distinct().toTypedArray()
    }

    companion object {
        fun empty(): CityIndex = CityIndex(emptyList(), emptyList())

        fun load(input: InputStream): CityIndex {
            val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8), 1 shl 16)
            val header = reader.readLine() ?: error("empty city file")
            require(header.startsWith("UFUQ-CITIES")) { "not a city file" }
            val zones = reader.readLine().split('\t').drop(1)
            val countries = reader.readLine().split('\t').drop(1)
            val cities = ArrayList<City>(30000)
            val alts = ArrayList<List<String>>(30000)
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) continue
                val f = line.split('\t')
                if (f.size < 7) continue
                cities += City(
                    name = f[0],
                    region = f[1],
                    country = countries.getOrElse(f[2].toInt()) { "" },
                    latitude = f[3].toDouble(),
                    longitude = f[4].toDouble(),
                    elevation = f[5].toIntOrNull() ?: 0,
                    zoneId = zones[f[6].toInt()]
                )
                alts += if (f.size > 7 && f[7].isNotEmpty()) f[7].split('|') else emptyList()
            }
            return CityIndex(cities, alts)
        }
    }
}

/** Text normalisation shared by the index and the query. */
object SearchText {
    private val marks = Regex("\\p{Mn}+")
    private val nonWord = Regex("[^\\p{L}\\p{Nd}]+")
    private val latinArticle = Regex("^(al|el|ar|as|ad|at|az|an|ash|adh|ath|ul)\\s+")

    fun normalize(input: String): String {
        var s = input.lowercase(Locale.ROOT)
        if (s.any { it.code > 127 }) {
            s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "")
            s = buildString(s.length) {
                for (ch in s) {
                    append(
                        when (ch) {
                            'ة' -> 'ه'
                            'ى', 'ی' -> 'ي'
                            'ک' -> 'ك'
                            'ٱ' -> 'ا'
                            'ـ' -> ' '
                            'ı' -> 'i'
                            'ø' -> 'o'
                            'æ' -> 'a'
                            'ß' -> 's'
                            else -> ch
                        }
                    )
                }
            }
        }
        s = s.replace(nonWord, " ").trim()
        if (s.isEmpty()) return s
        // Drop a leading article: "Al Hillah" -> "Hillah", "الرميثة" -> "رميثة".
        s = s.replace(latinArticle, "")
        if (s.startsWith("ال") && s.length > 3) s = s.substring(2)
        return s.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { foldLatin(it) }
    }

    /** Collapse common transliteration variants so "Rumaitha" ≈ "Rumaythah", "Samawa" ≈ "Samawah". */
    private fun foldLatin(word: String): String {
        if (word.any { it !in 'a'..'z' && it !in '0'..'9' }) return word
        var w = word
            .replace("ou", "u").replace("oo", "u").replace("ee", "i")
            .replace("ay", "ai").replace("ey", "ai").replace("ei", "ai")
            .replace('e', 'a').replace('o', 'u').replace('y', 'i')
            .replace("kh", "k").replace("q", "k").replace("dh", "z")
        // Collapse doubled letters.
        w = buildString(w.length) {
            for (ch in w) if (isEmpty() || last() != ch) append(ch)
        }
        if (w.length > 3 && w.endsWith("ah")) w = w.dropLast(1)
        if (w.length > 3 && w.endsWith("h") && w[w.length - 2] in "aiu") w = w.dropLast(1)
        return w
    }
}
