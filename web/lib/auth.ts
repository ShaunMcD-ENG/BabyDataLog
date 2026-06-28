import { compare, hash } from "bcryptjs";
import { getIronSession } from "iron-session";
import { cookies } from "next/headers";
import type { ReadonlyRequestCookies } from "next/dist/server/web/spec-extension/adapters/request-cookies";
import db from "@/lib/db/connection";

export interface SessionData {
  authenticated: boolean;
}

const SESSION_OPTIONS = {
  password: process.env.SESSION_SECRET ?? "dev-secret-replace-in-production-32chars",
  cookieName: "babydatalog-session",
  cookieOptions: {
    secure: process.env.NODE_ENV === "production",
    httpOnly: true,
    maxAge: 60 * 60 * 24 * 7, // 7 days
  },
};

export async function getSession(cookieStore?: ReadonlyRequestCookies) {
  return getIronSession<SessionData>(cookieStore ?? (await cookies()), SESSION_OPTIONS);
}

// Returns true if an admin password has been set in the DB.
// Catches SqliteError during Next.js static build when tables don't exist yet.
export function isSetupComplete(): boolean {
  try {
    const row = db
      .prepare("SELECT value FROM settings WHERE key = 'admin_password_hash'")
      .get() as { value: string } | undefined;
    return !!row;
  } catch {
    return false;
  }
}

export async function verifyAdminPassword(input: string): Promise<boolean> {
  const row = db
    .prepare("SELECT value FROM settings WHERE key = 'admin_password_hash'")
    .get() as { value: string } | undefined;
  if (!row) return false;
  return compare(input, row.value);
}

export async function setAdminPassword(password: string): Promise<void> {
  const hashed = await hash(password, 12);
  db.prepare(`INSERT OR REPLACE INTO settings (key, value) VALUES ('admin_password_hash', ?)`)
    .run(hashed);
}

export async function isAuthenticated(): Promise<boolean> {
  const session = await getSession();
  return session.authenticated === true;
}

export function isValidApiKey(apiKey: string): boolean {
  const device = db
    .prepare(`SELECT id FROM devices WHERE apiKey = ? AND status = 'approved'`)
    .get(apiKey);
  return !!device;
}
