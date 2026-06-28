import { NextRequest, NextResponse } from "next/server";
import { isAuthenticated, isValidApiKey } from "@/lib/auth";
import db from "@/lib/db/connection";

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
  updatedAtMs?: number;
  createdAtMs?: number;
  [key: string]: unknown;
}

interface SyncPush {
  deviceId: string;
  table: SyncTable;
  records: SyncRecord[];
}

async function isAuthorized(req: NextRequest): Promise<boolean> {
  const authHeader = req.headers.get("authorization");
  if (authHeader?.startsWith("Bearer ")) {
    return isValidApiKey(authHeader.slice(7));
  }
  return isAuthenticated();
}

// POST /api/sync — Android pushes changes; last-write-wins on conflict
export async function POST(req: NextRequest) {
  if (!(await isAuthorized(req))) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const body: SyncPush = await req.json();
  const { deviceId, table, records } = body;

  if (!SYNC_TABLES.includes(table)) {
    return NextResponse.json({ error: "Unknown table" }, { status: 400 });
  }

  const now = Date.now();
  const results = { inserted: 0, updated: 0, skipped: 0 };

  for (const record of records) {
    const existing = db
      .prepare(`SELECT * FROM ${table} WHERE syncUuid = ?`)
      .get(record.syncUuid) as SyncRecord | undefined;

    if (!existing) {
      const cols = Object.keys(record);
      const placeholders = cols.map(() => "?").join(", ");
      db.prepare(
        `INSERT OR IGNORE INTO ${table} (${cols.join(", ")}) VALUES (${placeholders})`
      ).run(...cols.map((c) => record[c] as unknown));
      db.prepare(
        `INSERT INTO sync_log (deviceId, table_name, syncUuid, action, syncedAtMs) VALUES (?,?,?,?,?)`
      ).run(deviceId, table, record.syncUuid, "push", now);
      results.inserted++;
    } else {
      const serverUpdatedAt = (existing.updatedAtMs ?? existing.createdAtMs ?? 0) as number;
      const deviceUpdatedAt = (record.updatedAtMs ?? record.createdAtMs ?? 0) as number;

      if (deviceUpdatedAt > serverUpdatedAt) {
        // Device version is newer — apply it
        const cols = Object.keys(record).filter((c) => c !== "id" && c !== "syncUuid");
        const assignments = cols.map((c) => `${c} = ?`).join(", ");
        db.prepare(`UPDATE ${table} SET ${assignments} WHERE syncUuid = ?`)
          .run(...cols.map((c) => record[c] as unknown), record.syncUuid);
        db.prepare(
          `INSERT INTO sync_log (deviceId, table_name, syncUuid, action, syncedAtMs) VALUES (?,?,?,?,?)`
        ).run(deviceId, table, record.syncUuid, "updated", now);
        results.updated++;
      } else {
        // Server version is same age or newer — keep it
        results.skipped++;
      }
    }
  }

  return NextResponse.json({ ok: true, ...results });
}

// GET /api/sync?lastSyncMs=... — Android pulls records updated since last sync
// Uses a 2-day lookback buffer so offline edits with slightly old timestamps are not missed.
// On first sync (lastSyncMs=0) this returns everything.
export async function GET(req: NextRequest) {
  if (!(await isAuthorized(req))) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { searchParams } = new URL(req.url);
  const lastSyncMs = parseInt(searchParams.get("lastSyncMs") ?? "0", 10);
  const TWO_DAYS_MS = 2 * 24 * 60 * 60 * 1000;
  const since = Math.max(0, lastSyncMs - TWO_DAYS_MS);

  const payload: Record<string, unknown[]> = {};

  for (const table of SYNC_TABLES) {
    payload[table] = db
      .prepare(`SELECT * FROM ${table} WHERE updatedAtMs > ?`)
      .all(since);
  }

  return NextResponse.json({ syncedAtMs: Date.now(), data: payload });
}
