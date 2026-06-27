import { NextRequest, NextResponse } from "next/server";
import { isAuthenticated } from "@/lib/auth";
import db from "@/lib/db/connection";

// POST — Android registers a new device (no auth — device has no key yet)
export async function POST(req: NextRequest) {
  const body = await req.json();
  const { deviceId, name, pairingCode } = body as {
    deviceId: string; name: string; pairingCode: string;
  };

  if (!deviceId || !name || !pairingCode) {
    return NextResponse.json({ error: "Missing required fields" }, { status: 400 });
  }

  const existing = db
    .prepare("SELECT id, status FROM devices WHERE deviceId = ?")
    .get(deviceId) as { id: number; status: string } | undefined;

  if (existing) {
    return NextResponse.json({ ok: true, status: existing.status });
  }

  db.prepare(
    `INSERT INTO devices (deviceId, name, pairingCode, status, registeredAtMs) VALUES (?,?,?,?,?)`
  ).run(deviceId, name, pairingCode, "pending", Date.now());

  return NextResponse.json({ ok: true, status: "pending" });
}

// GET — Admin lists all devices (session auth required)
export async function GET() {
  if (!(await isAuthenticated())) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const devices = db
    .prepare("SELECT * FROM devices ORDER BY registeredAtMs DESC")
    .all();

  return NextResponse.json({ devices });
}
