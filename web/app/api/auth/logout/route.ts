import { NextResponse } from "next/server";
import { getSession } from "@/lib/auth";
import { cookies } from "next/headers";

export async function POST() {
  const session = await getSession(await cookies());
  session.destroy();
  return NextResponse.json({ ok: true });
}
