import { NextRequest, NextResponse } from "next/server";
import { isAuthenticated } from "@/lib/auth";
import db from "@/lib/db/connection";

type Device = {
  id: number;
  deviceId: string;
  name: string;
  pairingCode: string;
  status: string;
  apiKey: string | null;
  registeredAtMs: number;
  approvedAtMs: number | null;
};

// GET — Android polls for approval status
// Returns apiKey only when status=approved AND pairingCode matches (avoids leaking key)
export async function GET(
  req: NextRequest,
  { params }: { params: Promise<{ deviceId: string }> }
) {
  const { deviceId } = await params;
  const pairingCode = req.nextUrl.searchParams.get("pairingCode");

  const device = db
    .prepare("SELECT * FROM devices WHERE deviceId = ?")
    .get(deviceId) as Device | undefined;

  if (!device) {
    return NextResponse.json({ error: "Device not found" }, { status: 404 });
  }

  if (device.status === "approved" && pairingCode === device.pairingCode) {
    return NextResponse.json({ status: "approved", apiKey: device.apiKey });
  }

  return NextResponse.json({ status: device.status });
}

// PATCH — Admin approves or rejects a device (session auth required)
export async function PATCH(
  req: NextRequest,
  { params }: { params: Promise<{ deviceId: string }> }
) {
  if (!(await isAuthenticated())) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { deviceId } = await params;
  const { action } = (await req.json()) as { action: "approve" | "reject" };

  const device = db
    .prepare("SELECT * FROM devices WHERE deviceId = ?")
    .get(deviceId) as Device | undefined;

  if (!device) {
    return NextResponse.json({ error: "Device not found" }, { status: 404 });
  }

  if (action === "approve") {
    // 64-char hex key (two UUIDs without dashes)
    const apiKey =
      crypto.randomUUID().replace(/-/g, "") +
      crypto.randomUUID().replace(/-/g, "");
    db.prepare(
      `UPDATE devices SET status = 'approved', apiKey = ?, approvedAtMs = ? WHERE deviceId = ?`
    ).run(apiKey, Date.now(), deviceId);
    return NextResponse.json({ ok: true });
  }

  if (action === "reject") {
    db.prepare("UPDATE devices SET status = 'rejected' WHERE deviceId = ?").run(deviceId);
    return NextResponse.json({ ok: true });
  }

  return NextResponse.json({ error: "Invalid action" }, { status: 400 });
}

// DELETE — Admin removes a device (session auth required)
export async function DELETE(
  _req: NextRequest,
  { params }: { params: Promise<{ deviceId: string }> }
) {
  if (!(await isAuthenticated())) {
    return NextResponse.json({ error: "Unauthorized" }, { status: 401 });
  }

  const { deviceId } = await params;
  db.prepare("DELETE FROM devices WHERE deviceId = ?").run(deviceId);
  return NextResponse.json({ ok: true });
}
