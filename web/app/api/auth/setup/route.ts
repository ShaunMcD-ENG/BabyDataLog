import { NextRequest, NextResponse } from "next/server";
import { isSetupComplete, setAdminPassword, getSession } from "@/lib/auth";
import { cookies } from "next/headers";

export async function POST(req: NextRequest) {
  if (isSetupComplete()) {
    return NextResponse.json({ error: "Server is already configured" }, { status: 403 });
  }

  const { password } = await req.json();

  if (!password || typeof password !== "string" || password.length < 8) {
    return NextResponse.json(
      { error: "Password must be at least 8 characters" },
      { status: 400 }
    );
  }

  await setAdminPassword(password);

  // Auto-login after setup so the user lands on the dashboard immediately
  const session = await getSession(await cookies());
  session.authenticated = true;
  await session.save();

  return NextResponse.json({ ok: true });
}
