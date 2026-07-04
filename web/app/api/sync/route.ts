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

const MINUTE_MS = 60_000;
const DAY_MS = 86_400_000;

// Columns that must never be written from a push payload: `id` is the
// device's local auto-increment key and colliding with server ids silently
// corrupts data; babySyncUuid is payload-only and resolved to a server babyId.
const STRIP_COLS = new Set(["id", "babySyncUuid"]);

// Find an existing row that represents the same real-world event as `record`
// even though it carries a different syncUuid (e.g. created before the
// deterministic-UUID scheme, or via a randomUUID fallback). Natural keys match
// the Android syncUuidFor() scheme: baby + minute of the event (+ title for
// milestones); babies match on name + day of birth.
function findByNaturalKey(table: SyncTable, record: SyncRecord): SyncRecord | undefined {
  if (table === "babies") {
    const name = String(record.name ?? "").trim().toLowerCase();
    const birth = Number(record.birthDateMs ?? 0);
    const dayStart = Math.floor(birth / DAY_MS) * DAY_MS;
    return db
      .prepare(
        `SELECT * FROM babies WHERE lower(trim(name)) = ? AND birthDateMs >= ? AND birthDateMs < ?`
      )
      .get(name, dayStart, dayStart + DAY_MS) as SyncRecord | undefined;
  }

  const babyId = Number(record.babyId ?? 0);
  const eventMs = Number(
    (table === "feeding_sessions" ? record.startTimeMs : record.timestampMs) ?? 0
  );
  if (!babyId || !eventMs) return undefined;
  const minStart = Math.floor(eventMs / MINUTE_MS) * MINUTE_MS;
  const timeCol = table === "feeding_sessions" ? "startTimeMs" : "timestampMs";

  if (table === "milestones") {
    return db
      .prepare(
        `SELECT * FROM milestones WHERE babyId = ? AND ${timeCol} >= ? AND ${timeCol} < ? AND lower(trim(title)) = ?`
      )
      .get(babyId, minStart, minStart + MINUTE_MS, String(record.title ?? "").trim().toLowerCase()) as
      | SyncRecord
      | undefined;
  }

  return db
    .prepare(`SELECT * FROM ${table} WHERE babyId = ? AND ${timeCol} >= ? AND ${timeCol} < ?`)
    .get(babyId, minStart, minStart + MINUTE_MS) as SyncRecord | undefined;
}

function recordTimestamp(r: SyncRecord): number {
  return (r.updatedAtMs ?? r.createdAtMs ?? 0) as number;
}

// LWW update of an existing row with the incoming record's data.
// Keeps the server row's id and syncUuid — the server is the identity master;
// devices adopt the server's syncUuid on their next pull.
function updateRow(table: SyncTable, existing: SyncRecord, incoming: SyncRecord) {
  const cols = Object.keys(incoming).filter(
    (c) => !STRIP_COLS.has(c) && c !== "syncUuid"
  );
  const assignments = cols.map((c) => `${c} = ?`).join(", ");
  db.prepare(`UPDATE ${table} SET ${assignments} WHERE syncUuid = ?`).run(
    ...cols.map((c) => incoming[c] as unknown),
    existing.syncUuid
  );
}

function insertRow(table: SyncTable, record: SyncRecord): boolean {
  const dbRecord = Object.fromEntries(
    Object.entries(record).filter(([k]) => !STRIP_COLS.has(k))
  );
  const cols = Object.keys(dbRecord);
  const placeholders = cols.map(() => "?").join(", ");
  const info = db
    .prepare(`INSERT INTO ${table} (${cols.join(", ")}) VALUES (${placeholders})`)
    .run(...cols.map((c) => dbRecord[c] as unknown));
  return info.changes > 0;
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

// POST /api/sync — Android pushes changes; last-write-wins on conflict.
// Records are matched by syncUuid first, then by natural key (baby + event
// minute) so the same event logged on two devices merges into one row.
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
  const errors: string[] = [];

  const logSync = db.prepare(
    `INSERT INTO sync_log (deviceId, table_name, syncUuid, action, syncedAtMs) VALUES (?,?,?,?,?)`
  );

  db.transaction(() => {
    for (let record of records) {
      try {
        // Child tables: resolve the server-side babyId from babySyncUuid.
        // Device-local babyIds must never reach the database — they belong to
        // a different id space and would violate or silently corrupt the FK.
        if (table !== "babies") {
          const uuid = typeof record.babySyncUuid === "string" ? record.babySyncUuid : "";
          const baby = uuid
            ? (db.prepare("SELECT id FROM babies WHERE syncUuid = ?").get(uuid) as
                | { id: number }
                | undefined)
            : undefined;
          if (!baby) {
            logger.warn("SYNC_PUSH_BABY_NOT_FOUND", { deviceId, table, babySyncUuid: uuid });
            errors.push(`${table}/${record.syncUuid}: baby not found (${uuid || "no babySyncUuid"})`);
            results.skipped++;
            continue;
          }
          record = { ...record, babyId: baby.id };
        }

        let existing = db
          .prepare(`SELECT * FROM ${table} WHERE syncUuid = ?`)
          .get(record.syncUuid) as SyncRecord | undefined;

        // No syncUuid match — the same real-world event may already exist
        // under a different uuid (pre-deterministic-uuid data). Merge instead
        // of inserting a duplicate.
        if (!existing) existing = findByNaturalKey(table, record);

        if (!existing) {
          if (insertRow(table, record)) {
            logSync.run(deviceId, table, record.syncUuid, "push", now);
            results.inserted++;
          } else {
            results.skipped++;
          }
        } else if (recordTimestamp(record) > recordTimestamp(existing)) {
          updateRow(table, existing, record);
          logSync.run(deviceId, table, existing.syncUuid, "updated", now);
          results.updated++;
        } else {
          results.skipped++;
        }
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e);
        logger.warn("SYNC_PUSH_RECORD_FAILED", { deviceId, table, syncUuid: record.syncUuid, error: msg });
        errors.push(`${table}/${record.syncUuid}: ${msg}`);
        results.skipped++;
      }
    }
  })();

  logger.info("SYNC_PUSH", { deviceId, table, ...results, errorCount: errors.length });
  return NextResponse.json({ ok: true, ...results, errors });
}

// GET /api/sync?lastSyncMs=... — Android pulls records updated since last sync.
// Uses a 2-day lookback buffer so offline edits with slightly old timestamps are not missed.
// Babies are ALWAYS returned in full so the client can remap child-record
// babyIds even when the baby row itself hasn't changed. Child rows carry
// babySyncUuid so clients never need to trust server-side integer ids.
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

  payload["babies"] = db.prepare(`SELECT * FROM babies`).all();

  for (const table of SYNC_TABLES) {
    if (table === "babies") continue;
    payload[table] = db
      .prepare(
        `SELECT t.*, b.syncUuid AS babySyncUuid
         FROM ${table} t JOIN babies b ON b.id = t.babyId
         WHERE t.updatedAtMs > ?`
      )
      .all(since);
  }

  const counts = Object.fromEntries(
    Object.entries(payload).map(([t, rows]) => [t, (rows as unknown[]).length])
  );
  logger.info("SYNC_PULL", { caller, lastSyncMs, since, counts });
  return NextResponse.json({ syncedAtMs: Date.now(), data: payload });
}
