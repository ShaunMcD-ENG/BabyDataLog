import { NextRequest, NextResponse } from "next/server";
import { isAuthenticated } from "@/lib/auth";
import db from "@/lib/db/connection";
import { runMigrations } from "@/lib/db/schema";

runMigrations();

// Tables included in sync, in insertion order (parents before children)
const SYNC_TABLES = [
  "babies",
  "feeding_sessions",
  "nappy_changes",
  "milestones",
  "growth_measurements",
] as const;

type SyncTable = (typeof SYNC_TABLES)[number];

interface SyncRecord {
  syncUuid: string;
  updatedAtMs?: number; // Android uses createdAtMs; future: add updatedAtMs
  [key: string]: unknown;
}

interface SyncPush {
  deviceId: string;
  table: SyncTable;
  records: SyncRecord[];
}

interface SyncPull {
  deviceId: string;
  lastSyncMs: number; // device sends its last successful sync timestamp
}

// POST /api/sync — Android pushes changes
export async function POST(req: NextRequest) {
  if (!(await isAuthenticated())) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const body: SyncPush = await req.json();
  const { deviceId, table, records } = body;

  if (!SYNC_TABLES.includes(table)) {
    return NextResponse.json({ error: "Unknown table" }, { status: 400 });
  }

  const now = Date.now();
  const results = { inserted: 0, conflicts: 0, skipped: 0 };

  const upsertStmt = db.prepare(`
    INSERT OR IGNORE INTO ${table} SELECT * FROM json_each(?) LIMIT 0
  `); // placeholder — real upsert below per-table

  for (const record of records) {
    const existing = db.prepare(
      `SELECT * FROM ${table} WHERE syncUuid = ?`
    ).get(record.syncUuid) as SyncRecord | undefined;

    if (!existing) {
      // New record — insert it
      const cols = Object.keys(record);
      const placeholders = cols.map(() => "?").join(", ");
      db.prepare(
        `INSERT OR IGNORE INTO ${table} (${cols.join(", ")}) VALUES (${placeholders})`
      ).run(...cols.map(c => record[c] as unknown));
      results.inserted++;

      db.prepare(
        `INSERT INTO sync_log (deviceId, table_name, syncUuid, action, syncedAtMs) VALUES (?,?,?,?,?)`
      ).run(deviceId, table, record.syncUuid, "push", now);
    } else {
      // Record exists — check for conflict
      const serverTs = (existing.createdAtMs as number) ?? 0;
      const deviceTs = (record.createdAtMs as number) ?? 0;

      if (JSON.stringify(existing) !== JSON.stringify(record)) {
        // Different data — log conflict for manual resolution
        db.prepare(`
          INSERT INTO sync_conflicts (table_name, syncUuid, deviceId, serverJson, deviceJson, createdAtMs)
          VALUES (?, ?, ?, ?, ?, ?)
        `).run(table, record.syncUuid, deviceId, JSON.stringify(existing), JSON.stringify(record), now);
        results.conflicts++;
      } else {
        results.skipped++;
      }
    }
  }

  return NextResponse.json({ ok: true, ...results });
}

// GET /api/sync?deviceId=...&lastSyncMs=... — Android pulls server changes
export async function GET(req: NextRequest) {
  if (!(await isAuthenticated())) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const lastSyncMs = parseInt(searchParams.get("lastSyncMs") ?? "0", 10);

  const payload: Record<string, unknown[]> = {};

  for (const table of SYNC_TABLES) {
    // Return rows created after the device's last sync
    const rows = db.prepare(
      `SELECT * FROM ${table} WHERE createdAtMs > ?`
    ).all(lastSyncMs);
    payload[table] = rows;
  }

  const pendingConflicts = db.prepare(
    `SELECT * FROM sync_conflicts WHERE resolvedAtMs IS NULL`
  ).all();

  return NextResponse.json({
    syncedAtMs: Date.now(),
    data: payload,
    pendingConflicts,
  });
}
