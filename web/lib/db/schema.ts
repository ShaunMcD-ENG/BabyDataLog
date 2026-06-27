import db from "./connection";

// Schema mirrors the Android Room database exactly so sync diffs are trivial.
// Column names and types match 1:1 with the Android entity definitions.

export function runMigrations() {
  db.exec(`
    CREATE TABLE IF NOT EXISTS babies (
      id           INTEGER PRIMARY KEY AUTOINCREMENT,
      syncUuid     TEXT    NOT NULL UNIQUE,
      name         TEXT    NOT NULL,
      birthDateMs  INTEGER NOT NULL,
      birthWeightGrams INTEGER,
      createdAtMs  INTEGER NOT NULL
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
      createdAtMs     INTEGER NOT NULL
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
      createdAtMs INTEGER NOT NULL
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
      createdAtMs INTEGER NOT NULL
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
      createdAtMs          INTEGER NOT NULL
    );

    -- Sync tracking: records which device last sent/received each record
    CREATE TABLE IF NOT EXISTS sync_log (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      deviceId    TEXT    NOT NULL,
      table_name  TEXT    NOT NULL,
      syncUuid    TEXT    NOT NULL,
      action      TEXT    NOT NULL, -- 'push' | 'pull' | 'conflict'
      resolvedBy  TEXT,             -- 'server' | 'device' | 'pending'
      syncedAtMs  INTEGER NOT NULL
    );

    -- Conflict queue: records where both device and server changed the same row
    CREATE TABLE IF NOT EXISTS sync_conflicts (
      id           INTEGER PRIMARY KEY AUTOINCREMENT,
      table_name   TEXT    NOT NULL,
      syncUuid     TEXT    NOT NULL,
      deviceId     TEXT    NOT NULL,
      serverJson   TEXT    NOT NULL, -- JSON snapshot of server version
      deviceJson   TEXT    NOT NULL, -- JSON snapshot of device version
      createdAtMs  INTEGER NOT NULL,
      resolvedAtMs INTEGER,
      resolution   TEXT              -- 'keep_server' | 'keep_device'
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
}
