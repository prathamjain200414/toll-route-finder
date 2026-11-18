# Toll Route Finder

A REST API that returns the toll plazas on the driving route between two Indian pincodes, in driving order, with
each plaza's distance from the source.

| | |
|---|---|
| **Live map demo** | https://toll-route-finder.onrender.com/ |
| **API** | `POST https://toll-route-finder.onrender.com/api/v1/toll-plazas` |
| **Swagger UI** | https://toll-route-finder.onrender.com/swagger-ui.html |
| **Postman** | [`postman/toll-route-finder.postman_collection.json`](postman/toll-route-finder.postman_collection.json) |

> Hosted on Render's free tier. After 15 idle minutes the server sleeps, and the first request then takes up to
> ~2 minutes to wake it. Requests after that take milliseconds (cached) to a few seconds (new route).

**Accuracy against Mappls (560064 to 411045):** we return 12 of the 14 tolls Mappls lists (86%), with no false
positives. Our distance is 854.0 km and Mappls' is 854.28 km. Our distance-from-source figures are within about
2 km of Mappls'. [Details below.](#accuracy-against-mappls)

---

## Contents

- [The API](#the-api)
- [Beyond the brief: trip planning and toll audit](#beyond-the-brief-trip-planning-and-toll-audit)
- [How it works](#how-it-works)
- [Accuracy against Mappls](#accuracy-against-mappls)
- [Design decisions](#design-decisions)
- [Running locally](#running-locally)
- [Testing](#testing)
- [Deployment](#deployment)
- [Limitations and next steps](#limitations-and-next-steps)

## The API

### `POST /api/v1/toll-plazas`

```bash
curl -X POST https://toll-route-finder.onrender.com/api/v1/toll-plazas \
  -H 'Content-Type: application/json' \
  -d '{"sourcePincode": "560064", "destinationPincode": "411045"}'
```

```json
{
  "route": { "sourcePincode": "560064", "destinationPincode": "411045", "distanceInKm": 854.0 },
  "tollPlazas": [
    { "name": "Banglaore-Nelamangala Toll Plaza", "latitude": 13.09062, "longitude": 77.400019, "distanceFromSource": 27.8 },
    { "name": "Kulumapalya Toll Plaza", "latitude": 13.1149338, "longitude": 77.3750936, "distanceFromSource": 31.8 },
    { "name": "Chokkenahalli Toll Plaza", "latitude": 13.3020161, "longitude": 77.1620066, "distanceFromSource": 63.2 }
  ]
}
```

The real response lists all 12 plazas; it is shortened here. The response shape is exactly the one in the
specification, and a route with no tolls returns `"tollPlazas": []`. Two response headers help with debugging:

- `X-Cache`: `HIT` or `MISS`.
- `X-Route-Provider`: the routing engine that produced the route.

### Errors

Every error uses the specification's `{"error": "..."}` body. Where it helps, a `details` array is added.

| Case | Status | Body |
|---|---|---|
| Malformed pincode (not 6 digits, or starts with 0 or 9) | 400 | `{"error":"Invalid source or destination pincode","details":["sourcePincode: must be a 6-digit Indian pincode starting with 1-8"]}` |
| Well-formed but non-existent pincode, e.g. `100000` | 400 | `{"error":"Invalid source or destination pincode","details":["Pincode 100000 was not found"]}` |
| Same source and destination | 400 | `{"error":"Source and destination pincodes cannot be the same"}` |
| Missing or invalid JSON body | 400 | `{"error":"Request body is missing or is not valid JSON"}` |
| No road route, e.g. Port Blair `744101` to Chennai `600001` | 422 | `{"error":"No drivable route exists between the given locations"}` |
| Routing engines down | 503 + `Retry-After: 30` | `{"error":"Routing service is temporarily unavailable, please retry shortly"}` |
| Wrong method or content type | 405 / 415 | `{"error":"..."}` |

Internal errors return `{"error":"Internal server error"}`, and stack traces never leak.

## Beyond the brief: trip planning and toll audit

I read FreightFox's product pages to see where a toll lookup would actually be used. Tolls come up in three places:

1. Your **Settle** content on [freight bill errors](https://www.freightfox.ai/blog/freight-payables-sap-s4hana-migration)
   names tolls among the charges that need checking.
2. **Track** uses FASTag toll records as a
   [location signal](https://www.freightfox.ai/blog/e-way-bill-rules-and-limits-2026).
3. Your [freight rate breakdown](https://www.freightfox.ai/blog/how-are-freight-rates-calculated) lists tolls as a
   line item.

So I added two endpoints and a map, all built on the same cached search. The required endpoint is unchanged.

### `POST /api/v1/trips/toll-audit`: reconcile a trip's toll charges

You send the plaza names from a FASTag statement or transporter invoice, spelled however the source spells them.
Each charge is checked against the planned route and gets one of these statuses:

| Status | Meaning | What it means for the business |
|---|---|---|
| `ON_ROUTE` | The plaza is on the planned route. | Legitimate charge. |
| `DUPLICATE_CHARGE` | The same plaza was already charged on this trip. | Possible FASTag double deduction. |
| `OFF_ROUTE` | The plaza exists, but `distanceFromRouteKm` away from the route. | Route deviation, or a wrong charge. |
| `UNKNOWN_PLAZA` | No plaza with that name exists. | Needs manual review. |
| `notCharged` (separate list) | A plaza on the route that was never charged. | Tracking gap, or the truck took another road. |

The verdict is `CLEAN` or `REVIEW`. Names are matched despite spelling variation:
- Typos within 2 edits.
- One name containing the other, as in "Banglaore-Nelamangala" and "Nelamangala".
- Transliteration folding: y/i, ee/i, doubled letters, and the inherent "a" that some sources drop, as in
  "Karajeevanahalli" and "Karjeevanhally".

Feeding Mappls' own toll list into this endpoint reproduces the accuracy analysis below automatically. It flags the
two tolls we miss as `OFF_ROUTE`, at 31 km and 36 km from our route.

### `POST /api/v1/trips/plan`: what a dispatcher needs

This takes the same input as the toll search and adds:
- Estimated drive hours.
- An estimated arrival time at each plaza (`etaHoursFromStart`).
- E-way bill validity days under CGST Rule 138(10): one day per 200 km or part of it. Bengaluru to Pune at 854 km
  gives 5 days.
- The route as a GeoJSON `LineString`, simplified with Douglas-Peucker to about 100 m tolerance. That brings it
  from thousands of points down to about 400 (~10 KB).

### Map at `/`

The map page draws the route and numbered toll pins with Leaflet and OpenStreetMap, and lists the plazas with
distance and ETA. It also has a paste box for the toll audit; off-route charges show as red pins. It is one static
HTML file served by the same app, with no build step and no extra hosting.

## How it works

```mermaid
flowchart LR
    A[POST source, destination] --> B{Validate<br/>regex 1-8 + 5 digits}
    B --> C{Cache<br/>Caffeine}
    C -- hit --> R[Response]
    C -- miss --> D[Pincode to lat/lon<br/>offline dataset, 19,255 pincodes]
    D --> E[Route<br/>ORS driving-hgv, OSRM fallback]
    E --> F[Spatial prefilter<br/>STRtree over 1,447 plazas]
    F --> G[Exact distance from each candidate to the route<br/>keep within 1 km, compute distance along route]
    G --> R
```

1. **Pincode to coordinates (offline).** There's no free, reliable pincode geocoding API, so I built
   `data/pincodes.csv` from India Post's All India Pincode Directory (data.gov.in) with
   [`scripts/build_pincode_dataset.py`](scripts/build_pincode_dataset.py).
   - Each pincode has many post offices, and some have bad coordinates: (0, 0), swapped lat/lon, or hundreds of km
     off.
   - The script takes the median of each pincode's offices, drops offices more than 30 km from it, and averages
     the rest.
   - It excludes 9xxxxx pincodes, which belong to the Army Postal Service and have no fixed location.
   - The result covers 19,255 pincodes and loads into memory in milliseconds.
2. **Routing.**
   - The primary engine is [OpenRouteService](https://openrouteservice.org/) with its **`driving-hgv`** (truck)
     profile, which ignores routes trucks can't use. It is also the engine whose route out of Bengaluru matches
     Mappls'.
   - The public [OSRM](https://project-osrm.org/) server is the fallback.
   - Each engine is retried with backoff, and failing over to the other is automatic.
   - Routes that need a ferry, such as the Andamans to the mainland, are rejected with 422 instead of being
     reported as a 3,887 km "drive".
3. **Toll data cleaning, done once at startup.** The provided CSV has 2,385 rows for 1,447 physical plazas. Two
   records count as the same plaza when they meet any of these rules:
   - They are within 300 m of each other, whatever the names.
   - They are within 2 km and their names match after normalisation or differ by at most 2 edits.
   - They are within 1 km and one name contains the other.

   Rows named "nan" join a nearby named plaza. The cleaned set is upserted into PostgreSQL with one batched,
   idempotent `MERGE`, so restarts don't create duplicates. The service then answers queries from an in-memory
   spatial index.
4. **Matching plazas to the route.**
   - The R-tree returns the plazas whose bounding boxes touch the route, about 25 route points per box.
   - Each candidate is then projected exactly onto the route's segments.
   - A plaza counts as on the route if it is within **1 km** of it.
   - `distanceFromSource` is the distance along the route to the projection point, not a straight-line distance.
   - A typical 850 km route has about 10,000 points. Checking every plaza against every point would take about
     14 million distance calculations; the index reduces that to a few hundred box queries plus exact checks on
     the few dozen candidates.
5. **Caching.**
   - Results are cached per (source, destination) pair in Caffeine: up to 2,000 entries, for 24 hours.
   - Each key is computed at most once at a time, so a burst of identical requests makes a single routing call.
   - Failures are never cached.
   - Direction matters, because toll plazas and routes can differ by direction.

## Accuracy against Mappls

The test case from the brief is 560064 (Bengaluru, Yelahanka) to 411045 (Pune, Baner). Mappls' default route is
"Via NH 50, NH 561A": 854.28 km with 14 tolls. Ours, from ORS `driving-hgv`, is 854.0 km with 12 tolls.

| # | Mappls | Mappls km | This API | Our km | Match |
|---|---|---:|---|---:|---|
| 1 | Navayuga Bengaluru | 18.6 | Banglaore-Nelamangala | 27.8 | likely ¹ |
| 2 | Kulumepalya | 33.9 | Kulumapalya | 31.8 | ✅ |
| 3 | Chokkanahalli | – | Chokkenahalli | 63.2 | ✅ |
| 4 | Karajeevanahalli | 107.5 | Karjeevanhally | 105.6 | ✅ |
| 5 | Guilalu | 176.3 | Guilalu | 174.4 | ✅ |
| 6 | Kannanakatte | 239.3 | Kananakatte | 237.7 | ✅ |
| 7 | Thimmalapura | 302.4 | Thimmalapura | 301.1 | ✅ |
| 8 | Hitnal | 336.1 | Hitnal | 334.5 | ✅ |
| 9 | Shahapur | 340.6 | Shahapur | 339.4 | ✅ |
| 10 | Vanagari | 394.9 | Vanagera | 394.1 | ✅ |
| 11 | Nagarhalla | 455.5 | Nagarhalla | 454.7 | ✅ |
| 12 | Kasaba Bijapur | 517.2 | Bijapur | 516.7 | ✅ |
| 13 | Sardewadi Indapur | 694.2 | – | – | ❌ ² |
| 14 | Patas | 774.7 | – | – | ❌ ² |

**Result: 12/14 = 86% recall, within the 80–90% target, with 0 false positives.** Distances from the source agree
within about 2 km. The steady ~1.5 km offset comes from where each service places the 560064 start point.

¹ Navayuga is the company that operates the Bengaluru–Nelamangala tollway, so this is the same tollway. Our
   plaza's coordinate in the provided CSV is about 9 km further along it.

² Both plazas **are** in our data. They're missed because of the road chosen, not the matching. After Bijapur,
   Mappls follows NH65 through Indapur and Patas. Both ORS and OSRM instead take the shorter state highways through
   Pandharpur and Baramati, which pass 31–36 km away. I chose not to force the route through NH65, because that
   would mean tuning to one test case. A production version would use a commercial truck router or add waypoints
   for known freight corridors.

You can reproduce this with the opt-in live test, `./mvnw test -Dtest=LiveRouteAccuracyTest -Dlive=true`, or by
posting Mappls' list to `/api/v1/trips/toll-audit`.

## Design decisions

- **The route engine sits behind an interface, with retry and fallback.** `RouteProvider` is a strategy with an
  ORS adapter and an OSRM adapter. `RetryingRouteProvider` (a decorator) and `FallbackRouteProvider` (a chain)
  combine them in configuration. Free routing APIs have rate limits and outages, so neither engine is a single
  point of failure. The rest of the code never knows which engine answered.
- **Toll data is in PostgreSQL but queried from memory.** The database is the durable, deduplicated record, and
  re-imports are idempotent `MERGE`s. But 1,447 points fit in a few KB, and an R-tree in memory answers spatial
  queries in microseconds without needing PostGIS on a free database.
- **"On the route" uses a 1 km buffer, chosen by measurement.** Plaza coordinates in the source data are often a
  few hundred metres off the road. I measured three buffers on the Bengaluru–Pune route:

  | Buffer | Plazas found | Against Mappls |
  |---|---:|---|
  | 0.5 km | 11 | Misses Hitnal, which Mappls lists. |
  | **1 km** | **12** | **All 12 are on Mappls' list.** |
  | 2 km | 15 | Adds 3 plazas Mappls doesn't list, on nearby roads: Tumkur Road (P7), Doddakarenahalli, Tumbadi. |

  The buffer is configurable as `toll-finder.matching.max-distance-from-route-km`.
- **Error responses never echo secrets.** The `DATABASE_URL` parser reports a malformed URL without including the
  URL, which contains the password. API keys are stripped of pasted whitespace, and no upstream response body
  reaches clients.
- **The extra endpoints don't change the required one.** The extensions are separate endpoints, so the specified
  contract stays exactly as written. A strict JSON test pins it.

## Running locally

You need Java 21. Maven is included through the `./mvnw` wrapper.

```bash
./mvnw spring-boot:run
# open http://localhost:8080/  (map)  and  http://localhost:8080/swagger-ui.html
```

With no environment variables, the app uses in-memory H2 and OSRM only. Optional environment variables:

| Variable | Purpose |
|---|---|
| `ORS_API_KEY` | Enables OpenRouteService truck routing as the primary engine (free key at openrouteservice.org). |
| `DATABASE_URL` | PostgreSQL. Accepts `postgresql://user:pass@host/db`, as Neon, Render and Heroku provide it, or a `jdbc:` URL. |
| `DATABASE_URL_UNPOOLED` | Direct (non-pooled) connection used only for Flyway migrations. |
| `PORT` | HTTP port (default 8080). |

Docker: `docker build -t toll-route-finder . && docker run -p 8080:8080 toll-route-finder`.

## Testing

```bash
./mvnw test            # 137 tests, about 20 s, offline and deterministic
```

The suite covers:
- **Unit tests:** geometry (haversine, projection, simplification), deduplication rules, name matching, CSV
  parsing, the pincode dataset, the e-way bill rule, and the toll audit.
- **HTTP adapter tests:** ORS and OSRM response parsing, ferry detection and error mapping, against a mock server.
- **Retry and fallback tests.**
- **Controller tests:** every error case, plus the response shape pinned with strict JSON.
- **Repository tests:** the idempotent `MERGE` upsert.
- **Full-application integration tests:** the real CSV import, index, cache and HTTP layer. Routing replays a
  recorded Bengaluru–Pune OSRM response, so they're deterministic and need no network.
- **`LiveRouteAccuracyTest`:** opt-in with `-Dlive=true`; calls the real routing engines.

JaCoCo coverage: **95% of instructions and 83% of branches**. The report is at `target/site/jacoco/index.html`.

## Deployment

All on free tiers, with no credit card:

- **[Render](https://render.com) (Docker web service, Singapore):** builds the [`Dockerfile`](Dockerfile), a
  multi-stage build that runs as a non-root user with JVM memory settings for 512 MB.
- **[Neon](https://neon.tech) serverless PostgreSQL (Singapore):** the app uses the pooled URL, and Flyway uses
  the direct URL.
- **OpenRouteService free key:** 2,000 routes a day. Caching keeps actual usage well below that.

## Limitations and next steps

- **Toll data is a static CSV.** New plazas need a re-import. The loader already upserts idempotently, so a
  scheduled sync from NHAI's data would slot in.
- **Free routers don't always pick the same corridor as commercial navigation** (see the Mappls notes above).
- **Toll costs are not returned.** No free per-plaza, per-vehicle-class fee data exists. With NHAI's fee tables,
  `trips/plan` could total the toll cost for a vehicle class, which is the "toll charges" line in a freight rate.
- **ETAs assume an even pace and no rest stops.** They are planning estimates, not live tracking.
- **The audit assumes a one-way trip.** A return journey would legitimately pass each plaza twice.

## Project layout

```
src/main/java/com/tollroutefinder/
  api/          controllers, DTOs, response mappers, global error handler
  search/       the cached toll plaza search (use case)
  trip/         trip plan helpers and toll audit
  routing/      RouteProvider + ORS / OSRM adapters, retry, fallback
  toll/         CSV reader, deduplicator, name matching, R-tree index, matcher, startup loader, JPA persistence
  pincode/      offline pincode locator
  geo/          haversine, route polyline projection and simplification
  config/       typed properties, cache, routing wiring, OpenAPI, DATABASE_URL conversion
src/main/resources/
  data/         toll_plazas.csv (provided), pincodes.csv (generated)
  db/migration  Flyway schema
  static/       map page
```
