import { NextRequest, NextResponse } from "next/server";
import { logger } from "@/lib/log";
import db from "@/lib/db/connection";

// POST /api/sync/devices/rename — a device renames itself.
//
// Authenticated by the device's own apiKey (not an admin session), and the
// device to rename is resolved FROM that apiKey rather than trusting a
// client-supplied id — a device can only ever rename itself.
//
// No merge/collision handling is needed: `devices.name` is a display label
// only (not unique, never used to attribute sync data — that's always keyed
// by the immutable deviceId), so a rename can never collide with another
// device or corrupt its history. Historical activity-log entries keep
// showing the name the device had at the time (see the sync_log.deviceName
// snapshot written by POST /api/sync), so renaming never rewrites the past.
export async function POST(req: NextRequest) {
  const authHeader = req.headers.get("authorization");
  const apiKey = authHeader?.startsWith("Bearer ") ? authHeader.slice(7) : null;
  if (!apiKey) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const device = db
    .prepare(`SELECT deviceId, name FROM devices WHERE apiKey = ? AND status = 'approved'`)
    .get(apiKey) as { deviceId: string; name: string } | undefined;

  if (!device) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { name } = (await req.json()) as { name?: string };
  const trimmed = name?.trim();
  if (!trimmed) {
    return NextResponse.json({ error: "Name is required" }, { status: 400 });
  }

  db.prepare(`UPDATE devices SET name = ? WHERE deviceId = ?`).run(trimmed, device.deviceId);
  logger.info("DEVICE_RENAMED", { deviceId: device.deviceId, oldName: device.name, newName: trimmed });
  return NextResponse.json({ ok: true, name: trimmed });
}
