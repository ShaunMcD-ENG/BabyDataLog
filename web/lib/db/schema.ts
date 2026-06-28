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
      type        TEXT    NOT NULL,
      amount      TEXT    NOT NULL,
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

    const now = Date.now();
    for (const baby of babies) {
      const newUuid = deriveBabySyncUuid(baby.name, baby.birthDateMs);
      if (newUuid !== baby.syncUuid) {
        db.prepare("UPDATE babies SET syncUuid = ?, updatedAtMs = ? WHERE id = ?")
          .run(newUuid, now, baby.id);
      }
    }

    db.prepare("INSERT OR REPLACE INTO settings (key, value) VALUES ('migration_baby_uuid_v1', '1')").run();
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
