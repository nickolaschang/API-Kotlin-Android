# ApiApp — Full Project Guide

NIT3213 Android assignment. Student ID `s8131175`, Sydney campus.
Connects to `https://nit3213api.onrender.com/` to fetch and display food-themed dashboard data, with an OWASP vulnerability demonstration.

---

## Table of Contents

1. [App Overview](#app-overview)
2. [Screen Flow](#screen-flow)
3. [Architecture](#architecture)
4. [Dependency Injection (Hilt)](#dependency-injection-hilt)
5. [File Reference — Kotlin](#file-reference--kotlin)
   - [Bootstrap & DI](#bootstrap--di)
   - [Data Models](#data-models)
   - [API Service](#api-service)
   - [Repository](#repository)
   - [Login Screen](#login-screen)
   - [Dashboard Screen](#dashboard-screen)
   - [Details Screen](#details-screen)
   - [Vulnerability Demo Screen](#vulnerability-demo-screen)
6. [File Reference — Resources](#file-reference--resources)
   - [Layouts](#layouts)
   - [Drawables](#drawables)
   - [Values (Colors, Styles, Themes)](#values-colors-styles-themes)
7. [Design System](#design-system)
8. [Unit Tests](#unit-tests)
9. [Tech Stack Summary](#tech-stack-summary)
10. [API Endpoints](#api-endpoints)
11. [Common Edit Scenarios](#common-edit-scenarios)

---

## App Overview

The app has four screens:

| Screen | Purpose |
|--------|---------|
| **Login** | Authenticates with student ID + first name against the NIT3213 API |
| **Dashboard** | Displays entity cards (food dishes) with search, filter chips, and stats |
| **Details** | Full-screen view of a single entity with hero, quick info, and all properties |
| **Vuln Demo** | OWASP API Top 10 demonstration — unauthenticated data access + student ID enumeration |

---

## Screen Flow

```
┌──────────────────┐
│   LoginActivity   │  ← App launcher (MAIN intent)
│                    │
│  [Login Button]  ──────────► DashboardActivity ──► DetailsActivity
│  [Demo Mode]     ──────────► DashboardActivity (with hardcoded JSON, no API)
│  [Vuln Demo]     ──────────► VulnDemoActivity
└──────────────────┘
```

**Data passed between screens (via Intent extras):**

| From → To | Extra Key | Value |
|-----------|-----------|-------|
| Login → Dashboard | `"keypass"` | The topic string returned by the API (e.g. `"food"`) |
| Login → Dashboard | `"demoEntities"` | JSON string of hardcoded entities (Demo Mode only) |
| Dashboard → Details | `"entity"` | JSON string of the single entity `Map<String, String>` |

---

## Architecture

**Pattern:** MVVM (Model-View-ViewModel) + Repository + Hilt DI

```
┌─────────────────────────────────────────────────────────────┐
│  UI LAYER (Activities)                                       │
│  LoginActivity  DashboardActivity  DetailsActivity  VulnDemo │
│       │                │                                │    │
│       ▼                ▼                                ▼    │
│  LoginViewModel  DashboardViewModel            VulnDemoVM    │
│       │                │                           │         │
│       └────────────────┼───────────────────────────┘         │
│                        ▼                                     │
│                  AppRepository                               │
│                        │                                     │
│                        ▼                                     │
│                   ApiService (Retrofit interface)             │
│                        │                                     │
│                        ▼                                     │
│               OkHttpClient → HTTPS → nit3213api.onrender.com │
└─────────────────────────────────────────────────────────────┘
```

**How data flows on login:**
1. User taps Login → `LoginActivity` calls `LoginViewModel.login()`
2. ViewModel calls `AppRepository.login()` which calls `ApiService.login()`
3. Retrofit sends `POST /sydney/auth` with `{"username":"s8131175","password":"Nickolas"}`
4. API returns `{"keypass":"food"}`
5. Repository wraps it in `Result.success(LoginResponse)`
6. ViewModel emits `LoginState.Success(keypass="food")`
7. Activity observes the state change → starts `DashboardActivity` with `keypass="food"`
8. `DashboardViewModel.loadEntities("food")` → `AppRepository.getDashboard("food")` → `GET /dashboard/food`
9. API returns `{"entities":[{...},{...},...], "entityTotal": 10}`
10. ViewModel stores entities, computes stats/categories/filters, emits to LiveData
11. Activity observes `filteredEntities` → feeds `EntityAdapter` → RecyclerView renders cards

---

## Dependency Injection (Hilt)

Hilt wires up the entire networking stack automatically. You never call constructors manually.

**How it connects:**

```
ApiApp (@HiltAndroidApp)           ← Bootstraps Hilt's component tree
    │
    ▼
AppModule (@Module, @InstallIn(SingletonComponent))
    │
    ├── provideOkHttpClient()      → OkHttpClient (256 max connections, 60s timeouts)
    ├── provideRetrofit(client)    → Retrofit (base URL + Gson + OkHttp)
    ├── provideApiService(retro)   → ApiService (Retrofit generates implementation)
    └── provideAppRepository(api)  → AppRepository (wraps ApiService)
         │
         ▼
    LoginViewModel(@Inject repo)          ← Hilt injects AppRepository
    DashboardViewModel(@Inject repo)      ← same singleton instance
    VulnDemoViewModel(@Inject repo)       ← same singleton instance
```

**Annotations cheat sheet:**

| Annotation | Where | What it does |
|------------|-------|--------------|
| `@HiltAndroidApp` | `ApiApp.kt` | Generates the root Hilt component |
| `@AndroidEntryPoint` | Every Activity | Lets Hilt inject into that Activity |
| `@HiltViewModel` | Every ViewModel | Lets Hilt construct the ViewModel with `@Inject` deps |
| `@Module` + `@InstallIn` | `AppModule.kt` | Tells Hilt "here's how to build these objects" |
| `@Provides` + `@Singleton` | Provider methods | "Call this once, cache forever" |

---

## File Reference — Kotlin

### Bootstrap & DI

#### `ApiApp.kt`
**Path:** `app/src/main/java/com/example/apiapp/ApiApp.kt`

Empty `Application` subclass. Only exists to carry `@HiltAndroidApp`.

- **To edit:** Only touch this if you need to run code at app startup (e.g. initialize a logging library). Add it inside `onCreate()`.
- **Connected to:** `AndroidManifest.xml` via `android:name=".ApiApp"`

#### `di/AppModule.kt`
**Path:** `app/src/main/java/com/example/apiapp/di/AppModule.kt`

Provides the full networking graph: `OkHttpClient → Retrofit → ApiService → AppRepository`.

**Key functions:**

| Function | Returns | What it configures |
|----------|---------|-------------------|
| `provideOkHttpClient()` | `OkHttpClient` | Dispatcher with `maxRequests=256`, `maxRequestsPerHost=256`. Connect/read/write timeouts at 60s (for Render cold starts). |
| `provideRetrofit(client)` | `Retrofit` | Base URL `https://nit3213api.onrender.com/`, Gson converter, uses the OkHttpClient above. |
| `provideApiService(retrofit)` | `ApiService` | Retrofit generates the implementation of the interface at runtime. |
| `provideAppRepository(apiService)` | `AppRepository` | Wraps ApiService with error handling. |

- **To edit:** Change the base URL here if the API moves. Change timeout values here. Add HTTP interceptors (e.g. logging) to the OkHttpClient builder.
- **Connected to:** Every ViewModel (they all receive `AppRepository` through constructor injection)

---

### Data Models

#### `data/model/LoginRequest.kt`
**Path:** `app/src/main/java/com/example/apiapp/data/model/LoginRequest.kt`

```kotlin
data class LoginRequest(val username: String, val password: String)
```

Serialized by Gson into `{"username":"...","password":"..."}` and sent as the POST body.

- **To edit:** Add fields here if the API ever requires more login parameters.

#### `data/model/LoginResponse.kt`
**Path:** `app/src/main/java/com/example/apiapp/data/model/LoginResponse.kt`

```kotlin
data class LoginResponse(val keypass: String)
```

The `keypass` returned by the API doubles as:
1. A session token (proves you authenticated)
2. A topic selector (determines what dashboard data you get)

- **To edit:** Add fields if the API starts returning more data on login (e.g. user display name, token expiry).

#### `data/model/DashboardResponse.kt`
**Path:** `app/src/main/java/com/example/apiapp/data/model/DashboardResponse.kt`

```kotlin
data class DashboardResponse(
    val entities: List<Map<String, String>>,
    val entityTotal: Int
)
```

**Why `Map<String, String>` instead of a typed class?** The API returns different fields depending on the topic. Food has `dishName`/`origin`/`mealType`, animals has `species`/`habitat`, etc. A generic map handles all topics without code changes.

- **To edit:** If you want a typed model for a specific topic, create a new data class and add a second `getDashboard()` variant in `ApiService` that returns it.

---

### API Service

#### `data/api/ApiService.kt`
**Path:** `app/src/main/java/com/example/apiapp/data/api/ApiService.kt`

Retrofit interface — defines the HTTP contract. Three methods:

| Method | HTTP | Path | Returns | Used by |
|--------|------|------|---------|---------|
| `login()` | POST | `/{location}/auth` | `LoginResponse` | Normal login flow |
| `loginRaw()` | POST | `/{location}/auth` | `Response<LoginResponse>` | Vuln demo scan (needs raw HTTP status code) |
| `getDashboard()` | GET | `/dashboard/{keypass}` | `DashboardResponse` | Dashboard + vuln demo topic fetch |

**`login()` vs `loginRaw()`:** Both hit the same endpoint. `login()` throws `HttpException` on 4xx/5xx (convenient for normal login). `loginRaw()` wraps the response in `Response<>` so you can read `response.code()` without catching exceptions — critical for the vuln scan which makes thousands of calls per second.

- **To edit:** Add new API endpoints here. Follow the pattern: `suspend fun` + Retrofit annotation + return type.
- **Connected to:** `AppRepository` (calls these methods), `AppModule` (creates the implementation)

---

### Repository

#### `data/repository/AppRepository.kt`
**Path:** `app/src/main/java/com/example/apiapp/data/repository/AppRepository.kt`

Single data-access facade. All ViewModels go through this — never call `ApiService` directly from a ViewModel.

**Functions:**

| Function | What it does | Returns |
|----------|-------------|---------|
| `login(location, username, password)` | Calls `ApiService.login()`, catches `HttpException`, maps error codes to user-friendly messages | `Result<LoginResponse>` |
| `getDashboard(keypass)` | Calls `ApiService.getDashboard()`, same error handling | `Result<DashboardResponse>` |
| `enumerateStudent(studentId)` | Calls `ApiService.loginRaw()` with password `"x"`, maps HTTP status to `StudentStatus` enum | `StudentStatus` |

**`StudentStatus` enum:**

| Value | HTTP Code | Meaning |
|-------|-----------|---------|
| `EXISTS` | 400 | Student ID is valid, wrong password |
| `NOT_FOUND` | 404 | Student ID doesn't exist |
| `LOGGED_IN` | 200 | Correct credentials (shouldn't happen with password "x") |
| `UNKNOWN` | anything else | Unexpected response |
| `ERROR` | exception | Network failure |

**Error handling pattern:** Every function wraps its work in try/catch. `HttpException` is caught first (API errors), then generic `Exception` (network errors). The error body is read from `e.response()?.errorBody()?.string()`, and if empty, a fallback message is chosen based on the status code.

- **To edit:** Add new data operations here. Always return `Result<T>` so ViewModels get a clean success/failure without dealing with exceptions.
- **Connected to:** `ApiService` (injected), all three ViewModels (they inject this)

---

### Login Screen

#### `ui/login/LoginViewModel.kt`
**Path:** `app/src/main/java/com/example/apiapp/ui/login/LoginViewModel.kt`

**State machine:** `LoginState` sealed class

```
         ┌──────────┐
         │  (idle)   │  ← no state emitted yet
         └─────┬─────┘
               │ login() called
               ▼
         ┌──────────┐
         │ Loading   │  ← spinner shown, button disabled
         └─────┬─────┘
          ┌────┴────┐
          ▼         ▼
     ┌─────────┐ ┌───────┐
     │ Success  │ │ Error  │
     │ (keypass)│ │ (msg)  │
     └─────────┘ └───────┘
          │         │
     navigate    show error,
     to dash     re-enable btn
```

**`login()` function:**
1. Blank field check → short-circuit to `Error` state
2. Emit `Loading`
3. Launch coroutine → call `repository.login()`
4. `onSuccess` → emit `Success(keypass)`
5. `onFailure` → emit `Error(message)`

- **To edit:** Add "remember me" logic, add token storage, add input sanitization.

#### `ui/login/LoginActivity.kt`
**Path:** `app/src/main/java/com/example/apiapp/ui/login/LoginActivity.kt`

**What it renders:**
- Gradient hero with "Welcome" / "Culinary Explorer" title
- Campus dropdown (Sydney / Footscray)
- Student ID text input
- Password text input (with toggle visibility icon)
- Login button
- Demo Mode button (loads hardcoded entities)
- Vuln Demo button (top-right, opens VulnDemoActivity)

**Key wiring:**

| UI Element | → Calls | Effect |
|------------|---------|--------|
| Login button click | `viewModel.login(location, username, password)` | Triggers the login flow |
| Demo Mode button click | Serializes hardcoded entities as JSON, starts `DashboardActivity` with `demoEntities` extra | Bypasses API entirely |
| Vuln Demo button click | Starts `VulnDemoActivity` | No auth needed |
| `viewModel.loginState` observer | Switches on `Loading`/`Success`/`Error` | Shows spinner, navigates, or shows error |

**`locations` map:**
```kotlin
"Footscray" to "footscray"
"Sydney" to "sydney"
```
Display label → API path segment. The dropdown shows keys, the login call uses values.

- **To edit:** Change campus list in the `locations` map. Change greeting text directly in `setupHeader()`. Change demo entities in the `demoButton` click listener. Change the layout in `activity_login.xml`.
- **Connected to:** `LoginViewModel` (observes state), `DashboardActivity` (navigates to), `VulnDemoActivity` (navigates to)

---

### Dashboard Screen

#### `ui/dashboard/DashboardViewModel.kt`
**Path:** `app/src/main/java/com/example/apiapp/ui/dashboard/DashboardViewModel.kt`

The most complex ViewModel. Manages entity list + filtering + stats.

**State machine:** `DashboardState` sealed class

```
     ┌──────────┐
     │ Loading   │ ← spinner shown
     └─────┬─────┘
      ┌────┴────┐
      ▼         ▼
 ┌─────────┐ ┌───────┐
 │ Success  │ │ Error  │
 └─────────┘ └───────┘
```

Note: `Success` carries no data — the filtered entity list is a separate LiveData so filter changes don't re-trigger the loading state.

**LiveData exposed to the Activity:**

| LiveData | Type | What it drives |
|----------|------|----------------|
| `dashboardState` | `DashboardState` | Top-level loading/success/error visibility |
| `filteredEntities` | `List<Map<String, String>>` | The RecyclerView adapter's data |
| `filterCategories` | `List<String>` | The category chip labels (e.g. "All", "Breakfast", "Lunch/Dinner") |
| `stats` | `Stats` | The three numbers in the gradient header |

**Key functions:**

| Function | Trigger | What it does |
|----------|---------|-------------|
| `loadEntities(keypass)` | Activity calls on start | Fetches from API, stores in `allEntities`, computes categories/stats, runs `recompute()` |
| `setDemoEntities(entities)` | Demo Mode | Same as above but with hardcoded data |
| `setSearchQuery(query)` | Every keystroke in search bar | Updates `searchQuery`, runs `recompute()` |
| `setFilter(filter)` | Filter chip tap | Updates `activeFilter`, runs `recompute()` |
| `recompute()` | Called by all of the above | Filters `allEntities` by query + category, publishes to `_filteredEntities` |
| `computeCategories(entities)` | After entities load | Finds the categorical key (mealType/type/category), extracts unique values, prepends "All" |
| `computeStats(entities)` | After entities load | Counts total, unique origins, unique categories. Labels adapt to topic (Cuisines/Countries/Places) |
| `detectBadgeKey(entities)` | Used by recompute + computeCategories | Scans entity keys for preferred categorical fields: mealtype → type → category → class → genre → status |

**How filtering works:**
```
allEntities (full API response)
      │
      ├── searchQuery (text from search bar)
      │     └── matches if ANY field value contains the query (case-insensitive)
      │
      ├── activeFilter (selected chip label, or null for "All")
      │     └── matches if the detected badge key's value equals the filter
      │
      └──► filteredEntities (intersection of both filters)
```

- **To edit:** Change search logic in `recompute()`. Add new stat metrics in `computeStats()`. Change category detection priority in `detectBadgeKey()`.
- **Connected to:** `AppRepository` (fetches data), `DashboardActivity` (observes all LiveData)

#### `ui/dashboard/DashboardActivity.kt`
**Path:** `app/src/main/java/com/example/apiapp/ui/dashboard/DashboardActivity.kt`

Thin UI layer — delegates everything to the ViewModel.

**Setup functions (called in `onCreate`):**

| Function | What it does |
|----------|-------------|
| `setupRecyclerView()` | Creates `EntityAdapter` with item click → start `DetailsActivity`. Hooks up `LinearLayoutManager`. |
| `setupHeader()` | Sets greeting text + topic-aware title ("Culinary Explorer" / "Music Library" / etc). Wires Vuln Demo button. |
| `setupSearch()` | Adds `TextWatcher` → pushes every keystroke to `viewModel.setSearchQuery()`. Shows/hides clear (×) icon. |
| `setupFilterChips()` | Observes `viewModel.filterCategories` → dynamically creates `Chip` views in the `ChipGroup`. First chip ("All") is pre-selected. |
| `observeViewModel()` | Observes `dashboardState` (loading/error), `filteredEntities` (adapter data), `stats` (header numbers). |
| `loadData()` | Checks Intent for `demoEntities` vs `keypass`, calls the matching ViewModel method. |

- **To edit:** Change the topic title mapping in `setupHeader()`. Change how entities are passed to Details in `setupRecyclerView()`. Change the layout in `activity_dashboard.xml`.
- **Connected to:** `DashboardViewModel`, `EntityAdapter`, `DetailsActivity`, `VulnDemoActivity`

#### `ui/dashboard/EntityAdapter.kt`
**Path:** `app/src/main/java/com/example/apiapp/ui/dashboard/EntityAdapter.kt`

RecyclerView adapter. Binds entity data to card views.

**What each card shows:**
- Emoji avatar (colored circle with flag/food emoji)
- Title (dish name)
- Subtitle (up to 2 short fields joined with " • ")
- Badge chip (meal type, category, etc.)
- 2-line description preview
- "Read more →" link (visible when description > 80 chars)

**`onBindViewHolder()` does:**
1. Calls `EntityPresentation.from(entity, position)` to get all display data
2. Sets text on title, subtitle, emoji, description
3. Tints avatar circle background with `GradientDrawable.setColor()`
4. Shows/hides badge chip, sets badge text + colors
5. Shows/hides "Read more" link based on description length
6. Sets click listeners (card tap + read more tap → both fire `onItemClick`)

**`updateEntities()`** — replaces the backing list and calls `notifyDataSetChanged()`. Called by the Activity whenever `filteredEntities` changes.

- **To edit:** Change card layout in `item_entity.xml`. Change what fields are displayed by modifying `EntityPresentation`. Change the 80-char "Read more" threshold in `onBindViewHolder()`.
- **Connected to:** `EntityPresentation` (creates display data), `DashboardActivity` (owns this adapter)

#### `ui/dashboard/EntityPresentation.kt`
**Path:** `app/src/main/java/com/example/apiapp/ui/dashboard/EntityPresentation.kt`

Schema-agnostic presentation logic. Takes a raw `Map<String, String>` entity and produces ready-to-render display data.

**`from(entity, index)` factory method — field selection priority:**

| Field | How it's picked |
|-------|----------------|
| **Title** | First key ending in "name" or "title" → else first non-description key |
| **Subtitle** | Next 2 unused fields with values ≤ 40 chars, joined with " • " |
| **Badge** | First preferred categorical key: mealtype → type → category → status → class → genre |
| **Emoji** | Country flag from origin → type keyword emoji → title keyword emoji → "✨" fallback |
| **Avatar color** | Rotates through an 8-color palette based on list position |
| **Badge colors** | Breakfast=green, lunch=blue, dinner=purple, snack=amber, default=grey |

**Emoji maps (defined as companion object properties):**
- `countryFlags` — 30+ country names → flag emojis (Japan→🇯🇵, Italy→🇮🇹, etc.)
- `typeEmojis` — category keywords → emojis (breakfast→🥞, dinner→🍽️, mammal→🐾, etc.)
- `titleEmojis` — food name keywords → emojis (sushi→🍣, pizza→🍕, burger→🍔, etc.)

- **To edit:** Add new country flags to `countryFlags`. Add new food emojis to `titleEmojis`. Change badge color mapping in `pickBadgeColors()`. Change field detection priority in `pickTitleKey()` / `pickBadgeKey()`.
- **Connected to:** `EntityAdapter` (calls `from()` in `onBindViewHolder`), `DetailsActivity` (calls `from()` for hero rendering)

---

### Details Screen

#### `ui/details/DetailsActivity.kt`
**Path:** `app/src/main/java/com/example/apiapp/ui/details/DetailsActivity.kt`

**No ViewModel** — reads the entity JSON from Intent extras and renders it directly.

**Layout sections (top to bottom):**
1. **Gradient hero** — emoji + title + subtitle + colored badge (uses `EntityPresentation`)
2. **Quick info row** — 2 side-by-side `MaterialCardView` cards for short fields (e.g. Origin, Main Ingredient)
3. **Description card** — full description text
4. **All properties table** — raw dump of every key/value pair

**Key functions:**

| Function | What it does |
|----------|-------------|
| `populateQuickInfo(entity, title)` | Finds up to 2 short fields (≤30 chars, not title/description), creates card views programmatically |
| `populateAllProperties(entity)` | Loops every key/value → creates a label + value row |
| `formatKey(key)` | Converts camelCase to Title Case: "dishName" → "Dish Name" |
| `Int.dp` extension | Converts dp to pixels for programmatic layout |

- **To edit:** Change which fields appear in quick info by modifying the filter in `populateQuickInfo()`. Change layout in `activity_details.xml`. Add a "share" button, add a "back to list" FAB, etc.
- **Connected to:** `EntityPresentation` (for hero rendering), `DashboardActivity` (launched from card click)

---

### Vulnerability Demo Screen

#### `ui/vulndemo/VulnDemoViewModel.kt`
**Path:** `app/src/main/java/com/example/apiapp/ui/vulndemo/VulnDemoViewModel.kt`

Most complex ViewModel. Runs two independent demos:

**Demo 1: Unauthenticated Dashboard (BOLA)**

`fetchTopic(topic)` → calls `repository.getDashboard(topic)` directly (no login, no keypass).
Proves that `GET /dashboard/{topic}` needs no authentication.

State machine: `TopicState` (Idle → Loading → Success/Error)

**Demo 2: Student ID Enumeration (Broken Auth)**

`scanStudents(startId)` → launches a streaming worker pool that probes student IDs.

**How the scanner works:**

```
scanStudents(8131100) called
       │
       ▼
128 worker coroutines launched (Dispatchers.IO)
       │
       │  Each worker loop:
       │  1. offset.getAndIncrement()          ← AtomicInteger, lock-free
       │  2. id = "s${startId + offset}"       ← e.g. "s8131101"
       │  3. repository.enumerateStudent(id)   ← POST /sydney/auth
       │  4. if EXISTS or LOGGED_IN → found.add(id)  ← CopyOnWriteArrayList
       │  5. scanned.incrementAndGet()
       │  6. check stopRequested → repeat or exit
       │
       │  Meanwhile, uiPublisher coroutine:
       │  - Wakes every 150ms
       │  - Reads scanned.get() + found.toList()
       │  - Publishes ScanState.Scanning(scanned, found)
       │
       ▼
All workers join → uiPublisher cancelled → ScanState.Done emitted
```

**Thread safety:**
- `offset` — `AtomicInteger` → no two workers scan the same ID
- `scanned` — `AtomicInteger` → accurate count across 128 threads
- `found` — `CopyOnWriteArrayList` → safe concurrent reads + writes
- `stopRequested` — `@Volatile` boolean → all workers see the flag immediately

**Why 128 workers + 256 OkHttp connections?** The API has zero rate limiting. More workers = more throughput. OkHttp's default `maxRequestsPerHost=5` would bottleneck everything — raised to 256 in `AppModule`.

**Constants (companion object):**
- `WORKER_COUNT = 128` — number of parallel coroutines
- `UI_UPDATE_MS = 150L` — milliseconds between LiveData updates during scan

State machine: `ScanState` (Idle → Scanning → Done/Error)

**Control functions:**
- `stopScan()` — flips `stopRequested = true`, workers exit on next loop iteration
- `resetScan()` — stops + resets state to Idle

- **To edit:** Change `WORKER_COUNT` to adjust parallelism. Change `UI_UPDATE_MS` for faster/slower UI updates. Add more heuristics to `enumerateStudent()` in the repository. The `topics` list contains all 18 known API topics — add/remove as needed.
- **Connected to:** `AppRepository.enumerateStudent()` and `AppRepository.getDashboard()`, `VulnDemoActivity`

#### `ui/vulndemo/VulnDemoActivity.kt`
**Path:** `app/src/main/java/com/example/apiapp/ui/vulndemo/VulnDemoActivity.kt`

**Setup functions:**

| Function | What it does |
|----------|-------------|
| `setupTopicChips()` | Creates 18 chips from `viewModel.topics`. Each chip tap → `viewModel.fetchTopic(topic)` |
| `setupEnumeration()` | Wires scan button (Start/Stop toggle), reads start ID from input, calls `viewModel.scanStudents()` or `viewModel.stopScan()` |
| `observeStates()` | Observes both `topicState` and `scanState`, updates UI on every transition |
| `setScanButtonMode(button, scanning)` | Toggles button text + color: orange "Start" ↔ red "Stop" |
| `populateTopicResults(container, entities)` | Renders first 5 entities as compact outlined cards + "... and N more" |

- **To edit:** Change the layout in `activity_vuln_demo.xml`. Change the scan button colors in `setScanButtonMode()`. Change how many entities are previewed (currently 5) in `populateTopicResults()`.
- **Connected to:** `VulnDemoViewModel`, `LoginActivity` (launched from Vuln Demo button)

---

## File Reference — Resources

### Layouts

| File | Screen | Key elements |
|------|--------|-------------|
| `activity_login.xml` | Login | Gradient hero, floating card with inputs, campus dropdown, login/demo/vuln buttons |
| `activity_dashboard.xml` | Dashboard | Gradient hero with 3 stat cards, search bar, filter chip scroll, RecyclerView, empty state |
| `item_entity.xml` | Dashboard card | MaterialCardView with avatar circle, title, subtitle, badge chip, description, read more link |
| `activity_details.xml` | Details | NestedScrollView with gradient hero, back button, quick info row, description card, all properties container |
| `activity_vuln_demo.xml` | Vuln Demo | Warning banner, API1 section (topic chips + results), API2 section (start ID input, scan button, progress, results) |

### Drawables

| File | What it is | Used in |
|------|-----------|---------|
| `bg_hero_gradient.xml` | 135° gradient: `brand_gradient_start` → `brand_gradient_end` (orange → red) | Hero headers on all screens |
| `bg_avatar_circle.xml` | Oval shape with solid fill | Entity card avatar circles |
| `bg_chip_rounded.xml` | 12dp rounded rectangle | Badge chips on cards |
| `bg_search_bar.xml` | 28dp rounded rectangle with 1dp stroke | Dashboard search bar |
| `bg_stat_card.xml` | Translucent white (`#33FFFFFF`) rounded rectangle | Stat cards in gradient header |

### Values (Colors, Styles, Themes)

#### `colors.xml`
Brand palette + semantic colors:
- `brand_primary` / `brand_primary_dark` / `brand_accent` — main app colors
- `brand_gradient_start` / `brand_gradient_end` — hero gradient
- `surface_bg` / `card_bg` / `divider` — surface colors
- `text_primary` / `text_secondary` / `text_hint` / `text_on_brand` — text hierarchy
- `meal_breakfast_bg/fg`, `meal_lunch_bg/fg`, etc. — badge chip colors per meal type
- `avatar_1` through `avatar_8` — rotating avatar background palette

#### `styles.xml`
Centralized design system. Two groups:

**TextAppearance.App.\*** — typography tokens:
- `HeroGreeting`, `HeroTitle`, `HeroTitleLarge`, `HeroSubtitle` — white-on-gradient text
- `StatNumber`, `StatLabel` — stat card typography
- `SectionLabel` — uppercase section headers
- `CardTitle`, `CardSubtitle`, `CardDescription` — entity card text hierarchy
- `Body`, `Caption` — general purpose
- `Badge` — small bold uppercase (chip text)
- `ReadMore` — brand-colored link text

**Widget.App.\*** — component styles:
- `Card` / `Card.Item` — MaterialCardView defaults (20dp radius, 2dp elevation)
- `Button.Primary` — solid orange button
- `Button.TonalGlass` — translucent dark button (for use on gradients)
- `TextInputLayout` / `TextInputLayout.Dropdown` — outlined input with 14dp rounded corners
- `Chip.Filter` — filter chip with border

#### `themes.xml`
Single theme `Theme.ApiApp` (extends `Material3.Light.NoActionBar`):
- Maps brand colors to Material theme attributes (`colorPrimary`, `colorSecondary`, etc.)
- Sets window/status/navigation bar colors
- Sets default text colors
- Only overrides `materialCardViewStyle` at theme level — all other widgets use explicit `style="..."` in layouts

---

## Design System

**Rule:** Widget styles (Button, TextInputLayout, Chip) are applied **explicitly** in XML via `style="@style/Widget.App.Button.Primary"` rather than as theme defaults. This avoids Material 3 theme-default inflation quirks where flat colors crash `TextInputLayout`.

**Only `materialCardViewStyle`** is set at the theme level because every card in the app uses the same shape and it's safe to override globally.

**To keep styles consistent when adding new screens:**
1. Use `TextAppearance.App.*` for all text (`android:textAppearance="@style/TextAppearance.App.CardTitle"`)
2. Use `Widget.App.*` for all interactive components (`style="@style/Widget.App.Button.Primary"`)
3. Use colors from `colors.xml` — never hardcode hex values in layouts
4. Use the gradient drawables for hero headers

---

## Unit Tests

| File | What it tests |
|------|-------------|
| `LoginViewModelTest.kt` | Blank field validation, success navigation, error messages |
| `DashboardViewModelTest.kt` | Search filtering, category filter, stats computation, demo mode |
| `AppRepositoryTest.kt` | HTTP error mapping, network error handling, enumeration status codes |

**Test dependencies:** JUnit 4, Mockito-Kotlin, `kotlinx-coroutines-test`, `arch-core-testing` (`InstantTaskExecutorRule`).

Run all tests: `./gradlew test`

---

## Tech Stack Summary

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |
| UI Framework | Material Design 3 |
| DI | Hilt (Dagger-based) |
| Networking | Retrofit 2 + OkHttp + Gson |
| Async | Kotlin Coroutines (`viewModelScope`, `Dispatchers.IO`) |
| Reactive | LiveData + MutableLiveData |
| Build System | Gradle KTS with version catalog (`libs.versions.toml`) |
| Annotation Processing | KSP (replaced kapt) |
| Testing | JUnit 4 + Mockito-Kotlin + coroutines-test + arch-core-testing |

---

## API Endpoints

Base URL: `https://nit3213api.onrender.com/`

| Method | Path | Body | Success | Error codes |
|--------|------|------|---------|-------------|
| POST | `/{location}/auth` | `{"username":"...","password":"..."}` | `200 {"keypass":"food"}` | 400 (wrong password), 404 (no such user) |
| GET | `/dashboard/{keypass}` | none | `200 {"entities":[...],"entityTotal":N}` | 404 (unknown topic) |

**Locations:** `sydney`, `footscray`

**Known topics (usable as keypass):** food, music, movies, books, travel, animals, science, history, art, technology, sports, plants, languages, fashion, architecture, mythology, fitness, photography

---

## Common Edit Scenarios

### "I want to change the API base URL"
→ `di/AppModule.kt` → `provideRetrofit()` → change the `.baseUrl()` string

### "I want to add a new API endpoint"
→ `data/api/ApiService.kt` — add a new `suspend fun` with Retrofit annotation
→ `data/repository/AppRepository.kt` — add a wrapper function that returns `Result<T>`
→ Call it from the relevant ViewModel

### "I want to change what the dashboard cards look like"
→ Layout: `res/layout/item_entity.xml`
→ Field selection: `ui/dashboard/EntityPresentation.kt`
→ Binding: `ui/dashboard/EntityAdapter.kt` → `onBindViewHolder()`

### "I want to add a new screen"
1. Create `ui/newscreen/NewActivity.kt` — annotate with `@AndroidEntryPoint`
2. Create `ui/newscreen/NewViewModel.kt` — annotate with `@HiltViewModel`, inject `AppRepository`
3. Create `res/layout/activity_new.xml`
4. Register in `AndroidManifest.xml`: `<activity android:name=".ui.newscreen.NewActivity" />`
5. Navigate from another Activity: `startActivity(Intent(this, NewActivity::class.java))`

### "I want to change the login greeting / dashboard title"
→ `ui/login/LoginActivity.kt` — hero text is in `activity_login.xml`
→ `ui/dashboard/DashboardActivity.kt` → `setupHeader()` — the `when` block maps topic to title

### "I want to add more food emojis"
→ `ui/dashboard/EntityPresentation.kt` → `titleEmojis` map (for food names) or `countryFlags` map (for countries)

### "I want to change the color scheme"
→ `res/values/colors.xml` — change `brand_primary`, `brand_gradient_start/end`, surface colors
→ `res/drawable/bg_hero_gradient.xml` — change gradient colors
→ `res/values/styles.xml` — if needed, adjust widget style color references

### "I want to adjust the vulnerability scanner speed"
→ `ui/vulndemo/VulnDemoViewModel.kt` → companion object:
  - `WORKER_COUNT` — more workers = more parallel requests (default 128)
  - `UI_UPDATE_MS` — lower = more frequent UI refresh (default 150ms)
→ `di/AppModule.kt` → `provideOkHttpClient()` — `maxRequestsPerHost` must be ≥ `WORKER_COUNT`
