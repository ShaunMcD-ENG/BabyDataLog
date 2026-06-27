import { NextRequest, NextResponse } from "next/server";
import { isAuthenticated } from "@/lib/auth";
import db from "@/lib/db/connection";

// POST /api/sync/resolve — resolve a pending conflict
// body: { conflictId: number, resolution: "keep_server" | "keep_device" }
export async function POST(req: NextRequest) {
  if (!(await isAuthenticated())) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { conflictId, resolution } = await req.json();

  if (resolution !== "keep_server" && resolution !== "keep_device") {
    return NextResponse.json({ error: "Invalid resolution" }, { status: 400 });
  }

  const conflict = db.prepare(
    `SELECT * FROM sync_conflicts WHERE id = ? AND resolvedAtMs IS NULL`
  ).get(conflictId) as {
    id: number; table_name: string; syncUuid: string;
    serverJson: string; deviceJson: string;
  } | undefined;

  if (!conflict) {
    return NextResponse.json({ error: "Conflict not found or already resolved" }, { status: 404 });
  }

  if (resolution === "keep_device") {
    const record = JSON.parse(conflict.deviceJson);
    const cols = Object.keys(record);
    const assignments = cols.map(c => `${c} = ?`).join(", ");
    db.prepare(
      `UPDATE ${conflict.table_name} SET ${assignments} WHERE syncUuid = ?`
    ).run(...cols.map(c => record[c]), record.syncUuid);
  }
  // keep_server: do nothing to the DB, just mark resolved

  db.prepare(
    `UPDATE sync_conflicts SET resolvedAtMs = ?, resolution = ? WHERE id = ?`
  ).run(Date.now(), resolution, conflictId);

  return NextResponse.json({ ok: true });
}
