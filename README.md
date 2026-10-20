# Ufuq — Qibla & Prayer Times

Ufuq (أفق, "horizon") is a tiny Android app that shows the Qibla and daily prayer times.
It's free, has no ads, collects nothing and works fully offline — the app has **no
INTERNET permission**.

- Prayer times from the standard solar formulas (praytimes.org algorithm), Ja'fari by default,
  plus Tehran, MWL, ISNA, Egyptian, Umm al-Qura, Karachi, Kuwait, Qatar, MUIS, Diyanet and Custom.
- Qibla compass with turn left / right guidance, true north from the offline geomagnetic model.
- Auto city (platform location only — no Google Play Services) or any of ~26,000 cities,
  searchable in English or Arabic with forgiving spelling.
- 30-day timetable, optional quiet notifications, manual adjustments.
- Android 6.0 (API 23) and newer; targets Android 16 (API 36).

## Build

Requirements: JDK 17+, Android SDK (platform 36), Gradle 9.6, Python 3.

```sh
python3 tools/build_cities.py                    # GeoNames -> app/src/main/assets/cities.tsv
(cd tools/reference && npm install && npm run gen) # reference times for the unit tests
gradle :app:testDebugUnitTest                    # engine, Qibla, DST and search tests
gradle :app:assembleDebug                        # app/build/outputs/apk/debug/app-debug.apk
```

Without the city file the app still runs; city search and auto city names are just empty.

## Tests

Every push runs `.github/workflows/ci.yml`:

1. **Unit tests** — every method in nine cities (Surrey, Makkah, Karbala, Qum, London,
   Jakarta, Istanbul, Toronto, Tromsø) at both solstices and equinoxes, within ±1 minute of
   the praytimes.org reference (`tools/reference`, regenerated with `npm run gen`); Qibla within
   0.1° of an independent implementation; daylight-saving transitions; manual cities in other
   time zones; polar day; Ramadan Isha rule; forgiving and Arabic search.
2. **Lint** — fails the build if code calls an API missing on older Android versions.
3. **Release checks** — APK size and that no INTERNET permission is present.
4. **Device tests** — the app is clicked through on six emulators (Android 6, 8, 11, 13, 15,
   16; small phone, tablet, large text; with and without Google Play Services), with
   screenshots of every screen and cold-start timing uploaded as build artifacts.

## Notes and decisions

- One warm light theme everywhere; no dark mode, and force-dark is disabled.
- The Today screen and timetable show Sunset only when it differs from Maghrib, and no Midnight row.
- Sunrise/sunset use sea-level horizon (0.833°), like most mosque timetables; elevation from the
  city data is not applied because a city's ground height rarely matches the observer's horizon.
- Umm al-Qura's Ramadan Isha (120 min) uses the arithmetic Hijri calendar, which can be a day
  off at the start or end of Ramadan.
- No background location: notifications use the location from the last time the app was opened.
- The rating prompt simply opens the store page after a week of use (no Play Services library).
- Method parameters are the commonly published values; verify each against its issuing body
  before release.

City data © [GeoNames](https://www.geonames.org/), CC-BY 4.0.
