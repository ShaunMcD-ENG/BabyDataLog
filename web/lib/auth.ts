import { compare } from "bcryptjs";
import { getIronSession } from "iron-session";
import { cookies } from "next/headers";
import type { ReadonlyRequestCookies } from "next/dist/server/web/spec-extension/adapters/request-cookies";

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

export async function verifyAdminPassword(input: string): Promise<boolean> {
  const hash = process.env.ADMIN_PASSWORD_HASH;
  if (!hash) return false;
  return compare(input, hash);
}

export async function isAuthenticated(): Promise<boolean> {
  const session = await getSession();
  return session.authenticated === true;
}
