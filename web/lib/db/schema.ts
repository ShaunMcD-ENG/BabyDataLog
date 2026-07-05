import { createHash } from "crypto";
import db from "./connection";

// Schema mirrors the Android Room database exactly so sync diffs are trivial.
// Column names and types match 1:1 with the Android entity definitions.

// RFC 4122 v3 UUID from a UTF-8 string — identical algorithm to
// Java's UUID.nameUUIDFromBytes so server and Android produce the same value.
function nameUUIDFromString(input: string): string {
  const hash = createHash("md5").update(Buffer.from(input, "utf8")).digest();
  hash[6] = (hash[6] & 0x0f) | 0x30; // version 3
  hash[8] = (hash[8] & 0x3f) | 0x80; // variant
  const h = hash.toString("hex");
  return `${h.slice(0,8)}-${h.slice(8,12)}-${h.slice(12,16)}-${h.slice(16,20)}-${h.slice(20,32)}`;
}

function floorToDay(ms: number): number {
  return Math.floor(ms / 86_400_000) * 86_400_000;
}

function deriveBabySyncUuid(name: string, birthDateMs: number): string {
  const key = `b:${name.trim().toLowerCase()}:${floorToDay(birthDateMs)}`;
  return nameUUIDFromString(key);
}

export function runMigrations() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS babies (
      id               INTEGER PRIMARY KEY AUTOINCREMENT,
      syncUuid         TEXT    NOT NULL UNIQUE,
      name             TEXT    NOT NULL,
      birthDateMs      INTEGER NOT NULL,
      birthWeightGrams INTEGER,
      createdAtMs      INTEGER NOT NULL,
      updatedAtMs      INTEGER NOT NULL DEFAULT 0,
      deletedAtMs      INTEGER
    );

    CREATE TABLE IF NOT EXISTS feeding_sessions (
      id              INTEGER PRIMARY KEY AUTOINCREMENT,
      syncUuid        TEXT    NOT NULL UNIQUE,
      babyId          INTEGER NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
      startTimeMs     INTEGER NOT NULL,
      endTimeMs       INTEGER,
      durationMinutes REAL,
      breastSide      TEXT    NOT NULL,
      babyState       TEXT,
      latchQuality    TEXT,
      notes           TEXT,
      createdAtMs     INTEGER NOT NULL,
      updatedAtMs     INTEGER NOT NULL DEFAULT 0,
      deletedAtMs     INTEGER
    );

    CREATE TABLE IF NOT EXISTS nappy_changes (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      syncUuid    TEXT    NOT NULL UNIQUE,
      babyId      INTEGER NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
      timestampMs INTEGER NOT NULL,
      weeAmount   TEXT    NOT NULL DEFAULT 'NONE',
      pooAmount   TEXT    NOT NULL DEFAULT 'NONE',
      pooColour   TEXT,
      notes       TEXT,
      createdAtMs INTEGER NOT NULL,
      updatedAtMs INTEGER NOT NULL DEFAULT 0,
      deletedAtMs INTEGER
    );

    CREATE TABLE IF NOT EXISTS milestones (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      syncUuid    TEXT    NOT NULL UNIQUE,
      babyId      INTEGER NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
      timestampMs INTEGER NOT NULL,
      title       TEXT    NOT NULL,
      description TEXT,
      category    TEXT    NOT NULL,
      photoUri    TEXT,
      createdAtMs INTEGER NOT NULL,
      updatedAtMs INTEGER NOT NULL DEFAULT 0,
      deletedAtMs INTEGER
    );

    CREATE TABLE IF NOT EXISTS growth_measurements (
      id                   INTEGER PRIMARY KEY AUTOINCREMENT,
      syncUuid             TEXT    NOT NULL UNIQUE,
      babyId               INTEGER NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
      timestampMs          INTEGER NOT NULL,
      weightGrams          INTEGER,
      heightCm             REAL,
      headCircumferenceCm  REAL,
      footSizeMm           INTEGER,
      handSizeMm           INTEGER,
      legLengthCm          REAL,
      armLengthCm          REAL,
      backLengthCm         REAL,
      notes                TEXT,
      createdAtMs          INTEGER NOT NULL,
      updatedAtMs          INTEGER NOT NULL DEFAULT 0,
      deletedAtMs          INTEGER
    );

    -- Sync tracking
    CREATE TABLE IF NOT EXISTS sync_log (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      deviceId    TEXT    NOT NULL,
      table_name  TEXT    NOT NULL,
      syncUuid    TEXT    NOT NULL,
      action      TEXT    NOT NULL,
      resolvedBy  TEXT,
      syncedAtMs  INTEGER NOT NULL
    );

    -- Kept for schema compatibility; auto-resolved conflicts are no longer queued here
    CREATE TABLE IF NOT EXISTS sync_conflicts (
      id           INTEGER PRIMARY KEY AUTOINCREMENT,
      table_name   TEXT    NOT NULL,
      syncUuid     TEXT    NOT NULL,
      deviceId     TEXT    NOT NULL,
      serverJson   TEXT    NOT NULL,
      deviceJson   TEXT    NOT NULL,
      createdAtMs  INTEGER NOT NULL,
      resolvedAtMs INTEGER,
      resolution   TEXT
    );

    CREATE TABLE IF NOT EXISTS settings (
      key   TEXT NOT NULL PRIMARY KEY,
      value TEXT NOT NULL
    );

    CREATE TABLE IF NOT EXISTS devices (
      id             INTEGER PRIMARY KEY AUTOINCREMENT,
      deviceId       TEXT    NOT NULL UNIQUE,
      name           TEXT    NOT NULL,
      pairingCode    TEXT    NOT NULL,
      status         TEXT    NOT NULL DEFAULT 'pending',
      apiKey         TEXT    UNIQUE,
      registeredAtMs INTEGER NOT NULL,
      approvedAtMs   INTEGER
    );

    CREATE INDEX IF NOT EXISTS idx_feedings_babyId    ON feeding_sessions(babyId);
    CREATE INDEX IF NOT EXISTS idx_nappies_babyId     ON nappy_changes(babyId);
    CREATE INDEX IF NOT EXISTS idx_milestones_babyId  ON milestones(babyId);
    CREATE INDEX IF NOT EXISTS idx_growth_babyId      ON growth_measurements(babyId);
    CREATE INDEX IF NOT EXISTS idx_conflicts_resolved ON sync_conflicts(resolvedAtMs);
    CREATE INDEX IF NOT EXISTS idx_devices_apiKey     ON devices(apiKey);
  `);

  // Add updatedAtMs to any table that existed before this column was introduced.
  // PRAGMA table_info is safe to call repeatedly; ALTER TABLE only runs if needed.
  const DATA_TABLES = [
    "babies", "feeding_sessions", "nappy_changes", "milestones", "growth_measurements",
  ] as const;

  for (const table of DATA_TABLES) {
    const cols = db.prepare(`PRAGMA table_info(${table})`).all() as { name: string }[];
    if (!cols.some((c) => c.name === "updatedAtMs")) {
      db.prepare(`ALTER TABLE ${table} ADD COLUMN updatedAtMs INTEGER NOT NULL DEFAULT 0`).run();
      db.prepare(`UPDATE ${table} SET updatedAtMs = createdAtMs`).run();
    }
    if (!cols.some((c) => c.name === "deletedAtMs")) {
      db.prepare(`ALTER TABLE ${table} ADD COLUMN deletedAtMs INTEGER`).run();
    }
  }

  // One-time migration: split nappy_changes' single `type` (PEE/POO/BOTH) +
  // `amount` pair into independent weeAmount/pooAmount columns. Rebuilds the
  // table (SQLite can't drop the old NOT NULL columns in place) so a PEE-only
  // row becomes pooAmount='NONE' and vice versa, and BOTH copies its one
  // recorded amount into both new columns.
  const nappyCols = db.prepare(`PRAGMA table_info(nappy_changes)`).all() as { name: string }[];
  if (nappyCols.some((c) => c.name === "type")) {
    db.transaction(() => {
      db.exec(`
        CREATE TABLE nappy_changes_new (
          id          INTEGER PRIMARY KEY AUTOINCREMENT,
          syncUuid    TEXT    NOT NULL UNIQUE,
          babyId      INTEGER NOT NULL REFERENCES babies(id) ON DELETE CASCADE,
          timestampMs INTEGER NOT NULL,
          weeAmount   TEXT    NOT NULL DEFAULT 'NONE',
          pooAmount   TEXT    NOT NULL DEFAULT 'NONE',
          pooColour   TEXT,
          notes       TEXT,
          createdAtMs INTEGER NOT NULL,
          updatedAtMs INTEGER NOT NULL DEFAULT 0,
          deletedAtMs INTEGER
        );

        INSERT INTO nappy_changes_new
          (id, syncUuid, babyId, timestampMs, weeAmount, pooAmount, pooColour, notes, createdAtMs, updatedAtMs, deletedAtMs)
        SELECT
          id, syncUuid, babyId, timestampMs,
          CASE WHEN type = 'POO' THEN 'NONE' ELSE amount END,
          CASE WHEN type = 'PEE' THEN 'NONE' ELSE amount END,
          pooColour, notes, createdAtMs, updatedAtMs, deletedAtMs
        FROM nappy_changes;

        DROP TABLE nappy_changes;
        ALTER TABLE nappy_changes_new RENAME TO nappy_changes;

        CREATE INDEX IF NOT EXISTS idx_nappies_babyId ON nappy_changes(babyId);
        CREATE INDEX IF NOT EXISTS idx_nappies_updatedAt ON nappy_changes(updatedAtMs);
      `);
    })();
  }

  // One-time migration: re-derive baby syncUuids from name+birthdate to match
  // the Android deterministic UUID scheme introduced in DB v6.
  // Tracked via settings so it only runs once even if the container restarts.
  const migrationDone = db
    .prepare("SELECT value FROM settings WHERE key = 'migration_baby_uuid_v1'")
    .get();

  if (!migrationDone) {
    const babies = db
      .prepare("SELECT id, name, birthDateMs, syncUuid FROM babies")
      .all() as { id: number; name: string; birthDateMs: number; syncUuid: string }[];

    const CHILD_TABLES = [
      "feeding_sessions", "nappy_changes", "milestones", "growth_measurements",
    ] as const;

    const now = Date.now();

    db.transaction(() => {
      for (const baby of babies) {
        const newUuid = deriveBabySyncUuid(baby.name, baby.birthDateMs);
        if (newUuid === baby.syncUuid) continue;

        // Check if another baby already claimed this derived UUID (duplicate entry
        // e.g. same baby synced from two phones with different random UUIDs).
        const survivor = db
          .prepare("SELECT id FROM babies WHERE syncUuid = ? AND id != ?")
          .get(newUuid, baby.id) as { id: number } | undefined;

        if (survivor) {
          // Merge: move child records onto the surviving baby, then soft-delete this one.
          for (const t of CHILD_TABLES) {
            db.prepare(`UPDATE OR IGNORE ${t} SET babyId = ? WHERE babyId = ?`)
              .run(survivor.id, baby.id);
          }
          db.prepare("UPDATE babies SET deletedAtMs = ?, updatedAtMs = ? WHERE id = ?")
            .run(now, now, baby.id);
        } else {
          db.prepare("UPDATE babies SET syncUuid = ?, updatedAtMs = ? WHERE id = ?")
            .run(newUuid, now, baby.id);
        }
      }
    })();

    db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('migration_baby_uuid_v1', '1')").run();
  }

  // One-time migration: purge soft-deleted duplicate babies left behind by the
  // baby-uuid merge migration. Those tombstones share a natural key (name +
  // birth day) with the surviving live baby, which lets sync clients wrongly
  // adopt the tombstone's uuid and orphan every child record. Genuine baby
  // deletions (no live twin) are kept as tombstones.
  const purgeDone = db
    .prepare("SELECT value FROM settings WHERE key = 'migration_purge_merged_babies_v1'")
    .get();

  if (!purgeDone) {
    const CHILD_TABLES_P = [
      "feeding_sessions", "nappy_changes", "milestones", "growth_measurements",
    ] as const;

    const tombstones = db
      .prepare("SELECT id, name, birthDateMs FROM babies WHERE deletedAtMs IS NOT NULL")
      .all() as { id: number; name: string; birthDateMs: number }[];

    db.transaction(() => {
      for (const tomb of tombstones) {
        const dayStart = floorToDay(tomb.birthDateMs);
        const live = db
          .prepare(
            `SELECT id FROM babies
             WHERE deletedAtMs IS NULL AND id != ?
               AND lower(trim(name)) = ? AND birthDateMs >= ? AND birthDateMs < ?`
          )
          .get(tomb.id, tomb.name.trim().toLowerCase(), dayStart, dayStart + 86_400_000) as
          | { id: number }
          | undefined;
        if (!live) continue;

        for (const t of CHILD_TABLES_P) {
          db.prepare(`UPDATE ${t} SET babyId = ? WHERE babyId = ?`).run(live.id, tomb.id);
        }
        db.prepare("DELETE FROM babies WHERE id = ?").run(tomb.id);
      }
    })();

    db.prepare(
      "INSERT OR REPLACE INTO settings (key, value) VALUES ('migration_purge_merged_babies_v1', '1')"
    ).run();
  }

  // One-time migration: merge duplicate child records created before
  // natural-key matching existed (same event logged on two phones under
  // different random syncUuids). Keeps the most recently updated row.
  const dedupeDone = db
    .prepare("SELECT value FROM settings WHERE key = 'migration_dedupe_natural_v2'")
    .get();

  if (!dedupeDone) {
    const MINUTE_MS = 60_000;
    const CHILD_TABLES: { table: string; timeCol: string; extraKey?: string }[] = [
      { table: "feeding_sessions", timeCol: "startTimeMs" },
      { table: "nappy_changes", timeCol: "timestampMs" },
      { table: "growth_measurements", timeCol: "timestampMs" },
      { table: "milestones", timeCol: "timestampMs", extraKey: "title" },
    ];

    db.transaction(() => {
      for (const { table, timeCol, extraKey } of CHILD_TABLES) {
        const rows = db
          .prepare(`SELECT * FROM ${table}`)
          .all() as Record<string, unknown>[];

        const groups = new Map<string, Record<string, unknown>[]>();
        for (const row of rows) {
          const minute = Math.floor(Number(row[timeCol]) / MINUTE_MS);
          const extra = extraKey ? String(row[extraKey] ?? "").trim().toLowerCase() : "";
          const key = `${row.babyId}:${minute}:${extra}`;
          const group = groups.get(key) ?? [];
          group.push(row);
          groups.set(key, group);
        }

        const del = db.prepare(`DELETE FROM ${table} WHERE id = ?`);
        for (const group of groups.values()) {
          if (group.length < 2) continue;
          group.sort(
            (a, b) =>
              Number(b.updatedAtMs ?? b.createdAtMs ?? 0) -
              Number(a.updatedAtMs ?? a.createdAtMs ?? 0)
          );
          for (const loser of group.slice(1)) del.run(loser.id);
        }
      }
    })();

    db.prepare(
      "INSERT OR REPLACE INTO settings (key, value) VALUES ('migration_dedupe_natural_v2', '1')"
    ).run();
  }

  // Index for efficient pull filtering — safe to run if already exists
  db.exec(`
    CREATE INDEX IF NOT EXISTS idx_feedings_updatedAt   ON feeding_sessions(updatedAtMs);
    CREATE INDEX IF NOT EXISTS idx_nappies_updatedAt    ON nappy_changes(updatedAtMs);
    CREATE INDEX IF NOT EXISTS idx_milestones_updatedAt ON milestones(updatedAtMs);
    CREATE INDEX IF NOT EXISTS idx_growth_updatedAt     ON growth_measurements(updatedAtMs);
    CREATE INDEX IF NOT EXISTS idx_babies_updatedAt     ON babies(updatedAtMs);
  `);
}
