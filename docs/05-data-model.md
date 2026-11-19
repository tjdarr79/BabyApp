# 05 — Data Model, Persistence Schema, Versioning, Migration

**Storage:** Room (SQLite) for babies, snapshots, history and entitlements. DataStore for app-wide preferences. Engine snapshots are **versioned JSON blobs** owned by `:engine`, so engine evolution doesn't force SQL migrations.

**Kickoff rule 4:** adding baby #2 is **rows only, no migration**.

---

## 1. Entities and keys

| Entity | Key | Owner | Notes |
|---|---|---|---|
| `Baby` | `BabyId` (UUID v4 string) | app | Profile + config. `slot` 0–15 unique among non-deleted babies |
| `BabyRuntime` | `babyId` (PK, FK) | app | Run state, speed, time anchors, quiet hours |
| `BabySnapshot` | `babyId` (PK, FK) | engine (opaque to app) | Latest `BabyState` JSON + versions + checksum |
| `HistoryEvent` | `id` autoincrement, FK `babyId` | app (from engine events) | User-visible timeline |
| `ActionLog` | `id`, FK `babyId` | app | Every applied `CareAction`. Enables deterministic replay/debug. Bounded retention |
| `DailySummary` | (`babyId`, `localDate`) | app | Rollup after detailed history ages out |
| `Entitlement` | `sku` | app | Empty in v1. `EntitlementProvider` reads it in Step 10 (table exists now to avoid a migration) |
| `AlarmLatency` | `id` | app | Local reliability self-check (`03` §9). No PII, never transmitted |
| `AppPreferences` | — (DataStore) | app | Theme, global volume, haptics, onboarding flags, last permission prompts |

## 2. Schema (Room, DB version 1)

```sql
CREATE TABLE babies (
  id              TEXT    PRIMARY KEY NOT NULL,          -- BabyId (UUID)
  slot            INTEGER NOT NULL,                       -- 0..15, for derived alarm/notification ids
  name            TEXT    NOT NULL,
  age_band        TEXT    NOT NULL,                       -- enum name, e.g. 'M2_3'
  temperament     TEXT    NOT NULL,                       -- 'EASYGOING'|'AVERAGE'|'DEMANDING'|'HIGHLY_SENSITIVE'
  seed            INTEGER NOT NULL,                       -- 64-bit, drawn at creation
  night_start_min INTEGER NOT NULL DEFAULT 1140,          -- 19:00
  night_end_min   INTEGER NOT NULL DEFAULT 420,           -- 07:00
  prefs_json      TEXT    NOT NULL DEFAULT '{}',          -- per-baby UI/audio prefs (Step 10: per-baby sound set)
  created_at      INTEGER NOT NULL,
  updated_at      INTEGER NOT NULL,
  deleted_at      INTEGER                                  -- soft delete; slot freed when non-null
);
CREATE UNIQUE INDEX ux_babies_slot_active ON babies(slot) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_babies_seed ON babies(seed);

CREATE TABLE baby_runtime (
  baby_id         TEXT    PRIMARY KEY NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
  run_state       TEXT    NOT NULL,                       -- RUNNING|PAUSED|STOPPED|AUTO_PAUSED
  mode            TEXT    NOT NULL DEFAULT 'NORMAL',      -- NORMAL|ACCELERATED
  training_hints  INTEGER NOT NULL DEFAULT 0,             -- Training Mode overlay on/off
  speed           REAL    NOT NULL DEFAULT 1.0,
  sim_anchor      INTEGER NOT NULL,                       -- SimInstant at anchor
  wall_anchor     INTEGER NOT NULL,                       -- wall epoch ms at anchor
  quiet_hours_json TEXT   NOT NULL DEFAULT '{}',
  session_id      TEXT    NOT NULL,                       -- new id on Reset
  updated_at      INTEGER NOT NULL
);

CREATE TABLE baby_snapshots (
  baby_id               TEXT    PRIMARY KEY NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
  engine_schema_version INTEGER NOT NULL,
  param_set_version     INTEGER NOT NULL,
  sim_time              INTEGER NOT NULL,                 -- state.meta.lastAdvancedTo
  payload               TEXT    NOT NULL,                 -- kotlinx.serialization JSON of BabyState
  checksum              TEXT    NOT NULL,                 -- SHA-256 of payload (corruption detection)
  written_at            INTEGER NOT NULL
);

CREATE TABLE history_events (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  baby_id     TEXT    NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
  session_id  TEXT    NOT NULL,
  sim_time    INTEGER NOT NULL,
  wall_time   INTEGER NOT NULL,
  type        TEXT    NOT NULL,                           -- BabyEvent type name
  payload     TEXT    NOT NULL DEFAULT '{}',              -- small JSON (outcome, severity, durations)
  UNIQUE (baby_id, sim_time, type, payload)               -- idempotent re-insert after crash mid-write
);
CREATE INDEX ix_history_baby_time ON history_events(baby_id, sim_time DESC);

CREATE TABLE action_log (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  baby_id    TEXT    NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
  session_id TEXT    NOT NULL,
  sim_time   INTEGER NOT NULL,
  action     TEXT    NOT NULL,
  params     TEXT    NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_action_baby_time ON action_log(baby_id, sim_time);

CREATE TABLE daily_summaries (
  baby_id    TEXT    NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
  local_date TEXT    NOT NULL,                            -- ISO yyyy-MM-dd
  payload    TEXT    NOT NULL,                            -- feeds, sleep minutes, cry minutes, changes...
  PRIMARY KEY (baby_id, local_date)
);

CREATE TABLE entitlements (
  sku         TEXT    PRIMARY KEY NOT NULL,
  state       TEXT    NOT NULL,                           -- PURCHASED|PENDING|REVOKED
  token_hash  TEXT,                                       -- never the raw token
  updated_at  INTEGER NOT NULL
);

CREATE TABLE alarm_latency (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  scheduled_at INTEGER NOT NULL,
  fired_at    INTEGER NOT NULL,
  exact       INTEGER NOT NULL
);
```

## 3. Write path and consistency

One **transaction per host step** (alarm wake, app open, action):
1. Load snapshots for all RUNNING babies.
2. Engine `advance`/`apply` per baby (pure, in memory).
3. In one Room transaction: upsert `baby_snapshots`, insert `history_events` (ignore on unique conflict), insert `action_log`.
4. After commit: plan alarms (`04` §11.2), post notifications, submit audio directives.

If the process dies between 3 and 4, the next wake re-plans from the committed snapshots (alarms and notifications are **derived state**, never persisted as truth). If it dies before 3, the next wake re-advances from the older snapshot. Determinism makes the replayed events identical, and the unique index de-duplicates history.

Concurrency: a single `SimulationHost` mutex in-process. Receivers use `goAsync()` and the same host.

## 4. Versioning and migration strategy

| Layer | Version field | Strategy | Test |
|---|---|---|---|
| **SQL schema** | Room `version` | Room `exportSchema = true`, JSON schemas committed under `app/schemas/`. Prefer `AutoMigration`. Manual `Migration` with SQL for anything else. **Never** `fallbackToDestructiveMigration` in release | `MigrationTestHelper` test per version pair, run in CI |
| **Engine snapshot** | `engine_schema_version` | `kotlinx.serialization` with defaults for new fields (additive changes need no bump). Breaking changes bump the version and add an **upcaster** `vN → vN+1` (pure JSON transform in `:engine`). Chain applied on load | Golden-file tests: committed snapshots of every past version must load and advance |
| **Parameters** | `param_set_version` | Parameter files are versioned resources shipped in the APK. A snapshot stores the version it was created with. On load with newer params: thresholds are re-sampled at the next natural cycle boundary (no jump in current drives, since drives are normalized) | Test: load v1 snapshot with v2 params → advance 24 h → no exceptions, metrics in range |
| **History payloads** | implicit (type name) | Additive JSON only. Unknown types are rendered as generic entries | Render test with unknown type |

**Corruption handling:** checksum mismatch or failed deserialization → keep the profile, mark the snapshot invalid, re-initialize state at "now" (baby awake, calm, fed 1 h ago), and log a `CONFIG_CHANGED(reason=RECOVERED)` history entry. Never crash on load.

## 5. Multi-baby checklist (rule 4 proof)

| Need for baby #2 | Covered by v1 schema |
|---|---|
| Second profile | New `babies` row. Next free `slot` |
| Independent state | New `baby_snapshots` row |
| Independent run/pause/speed | New `baby_runtime` row |
| Separate history | Same `history_events` table, `baby_id` FK + index |
| Distinguishable audio/notification prefs | `babies.prefs_json` |
| Purchase entitlement | `entitlements` table (exists, empty) |
| Alarm/notification ids | Derived from `slot` |

Test (M4): an instrumented test inserts two babies via the DAO on a v1 database, runs the host for 24 simulated hours, and asserts no schema change and full per-baby isolation in queries.

## 6. Retention

- `history_events`: detailed for 30 days, then rolled into `daily_summaries` and deleted (daily housekeeping, inexact).
- `action_log`: 14 days (debug/replay window).
- `alarm_latency`: last 500 rows.
- Deleting a baby: soft delete (`deleted_at`) with 7-day undo, then hard delete cascades.
