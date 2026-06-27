import { NextRequest, NextResponse } from "next/server";
import { getSession, verifyAdminPassword } from "@/lib/auth";
import { cookies } from "next/headers";

export async function POST(req: NextRequest) {
  const { password } = await req.json();

  const valid = await verifyAdminPassword(password);
  if (!valid) {
    return NextResponse.json({ error: "Invalid password" }, { status: 401 });
  }

  const session = await getSession(await cookies());
  session.authenticated = true;
  await session.save();

  return NextResponse.json({ ok: true });
}
