import { NextResponse } from "next/server";
import { SYNC_SERVER_VERSION } from "@/lib/version";

// GET /api/version — unauthenticated deployment check.
// Reports which build is actually running so a stale Docker container is
// immediately identifiable (compare `commit` against the repo's HEAD).
export async function GET() {
  return NextResponse.json({
    serverVersion: SYNC_SERVER_VERSION,
    commit: process.env.GIT_SHA ?? "unknown",
    builtAt: process.env.BUILD_TIME ?? "unknown",
  });
}
