import { NextRequest, NextResponse } from "next/server";
import { isAuthenticated, isValidApiKey } from "@/lib/auth";
import { logger } from "@/lib/log";
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

async function resolveDeviceId(req: NextRequest): Promise<string | null> {
  const authHeader = req.headers.get("authorization");
  if (authHeader?.startsWith("Bearer ")) {
    const key = authHeader.slice(7);
    if (isValidApiKey(key)) return key.slice(0, 8);
  }
  if (await isAuthenticated()) return "admin";
  return null;
}

// POST /api/sync — Android pushes changes; last-write-wins on conflict
export async function POST(req: NextRequest) {
  const caller = await resolveDeviceId(req);
  if (!caller) {
    logger.warn("SYNC_PUSH_UNAUTHORIZED");
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const body: SyncPush = await req.json();
  const { deviceId, table, records } = body;

  if (!SYNC_TABLES.includes(table)) {
    return NextResponse.json({ error: "Unknown table" }, { status: 400 });
  }

  const now = Date.now();
  const results = { inserted: 0, updated: 0, skipped: 0 };

  // Columns that exist only in the push payload, not in the DB schema
  const PAYLOAD_ONLY_COLS = new Set(["babySyncUuid"]);

  for (let record of records) {
    // Remap babyId for child tables: local auto-increment IDs differ between
    // devices, so the client includes babySyncUuid for us to resolve the
    // correct server-side baby id.
    if (table !== "babies" && record.babySyncUuid) {
      const baby = db
        .prepare("SELECT id FROM babies WHERE syncUuid = ?")
        .get(record.babySyncUuid) as { id: number } | undefined;
      if (!baby) {
        logger.warn("SYNC_PUSH_BABY_NOT_FOUND", { deviceId, table, babySyncUuid: record.babySyncUuid });
        results.skipped++;
        continue;
      }
      record = { ...record, babyId: baby.id };
    }

    // Strip payload-only fields before building SQL
    const dbRecord = Object.fromEntries(
      Object.entries(record).filter(([k]) => !PAYLOAD_ONLY_COLS.has(k))
    ) as SyncRecord;

    const existing = db
      .prepare(`SELECT * FROM ${table} WHERE syncUuid = ?`)
      .get(dbRecord.syncUuid) as SyncRecord | undefined;

    if (!existing) {
      const cols = Object.keys(dbRecord);
      const placeholders = cols.map(() => "?").join(", ");
      db.prepare(
        `INSERT OR IGNORE INTO ${table} (${cols.join(", ")}) VALUES (${placeholders})`
      ).run(...cols.map((c) => dbRecord[c] as unknown));
      db.prepare(
        `INSERT INTO sync_log (deviceId, table_name, syncUuid, action, syncedAtMs) VALUES (?,?,?,?,?)`
      ).run(deviceId, table, dbRecord.syncUuid, "push", now);
      results.inserted++;
    } else {
      const serverUpdatedAt = (existing.updatedAtMs ?? existing.createdAtMs ?? 0) as number;
      const deviceUpdatedAt = (dbRecord.updatedAtMs ?? dbRecord.createdAtMs ?? 0) as number;

      if (deviceUpdatedAt > serverUpdatedAt) {
        const cols = Object.keys(dbRecord).filter((c) => c !== "id" && c !== "syncUuid");
        const assignments = cols.map((c) => `${c} = ?`).join(", ");
        db.prepare(`UPDATE ${table} SET ${assignments} WHERE syncUuid = ?`)
          .run(...cols.map((c) => dbRecord[c] as unknown), dbRecord.syncUuid);
        db.prepare(
          `INSERT INTO sync_log (deviceId, table_name, syncUuid, action, syncedAtMs) VALUES (?,?,?,?,?)`
        ).run(deviceId, table, dbRecord.syncUuid, "updated", now);
        results.updated++;
      } else {
        results.skipped++;
      }
    }
  }

  logger.info("SYNC_PUSH", { deviceId, table, ...results });
  return NextResponse.json({ ok: true, ...results });
}

// GET /api/sync?lastSyncMs=... — Android pulls records updated since last sync
// Uses a 2-day lookback buffer so offline edits with slightly old timestamps are not missed.
// On first sync (lastSyncMs=0) this returns everything.
export async function GET(req: NextRequest) {
  const caller = await resolveDeviceId(req);
  if (!caller) {
    logger.warn("SYNC_PULL_UNAUTHORIZED");
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

  const counts = Object.fromEntries(
    Object.entries(payload).map(([t, rows]) => [t, (rows as unknown[]).length])
  );
  logger.info("SYNC_PULL", { caller, lastSyncMs, since, counts });
  return NextResponse.json({ syncedAtMs: Date.now(), data: payload });
}
