"use server";
import { isAuthenticated } from "@/lib/auth";
import db from "@/lib/db/connection";

const ALLOWED_TABLES = [
  "babies",
  "feeding_sessions",
  "nappy_changes",
  "milestones",
  "growth_measurements",
  "sync_log",
  "devices",
] as const;

type AllowedTable = (typeof ALLOWED_TABLES)[number];

function assertAllowedTable(table: string): asserts table is AllowedTable {
  if (!(ALLOWED_TABLES as readonly string[]).includes(table)) {
    throw new Error(`Table "${table}" is not allowed`);
  }
}

function getColumns(table: string): string[] {
  return (
    db.prepare(`PRAGMA table_info(${table})`).all() as { name: string }[]
  ).map((c) => c.name);
}

export async function fetchRows(
  table: string,
  limit: number,
  orderBy: string,
  orderDir: string,
  where: string
): Promise<{ columns: string[]; rows: Record<string, unknown>[] }> {
  if (!(await isAuthenticated())) throw new Error("Unauthorized");
  assertAllowedTable(table);

  const cols = getColumns(table);
  const safeOrderBy = cols.includes(orderBy) ? orderBy : "id";
  const safeDir = orderDir === "ASC" ? "ASC" : "DESC";
  const safeLimit = Math.min(Math.max(1, Number(limit) || 100), 1000);

  let query = `SELECT * FROM ${table}`;
  if (where.trim()) query += ` WHERE ${where}`;
  query += ` ORDER BY "${safeOrderBy}" ${safeDir} LIMIT ${safeLimit}`;

  const rows = db.prepare(query).all() as Record<string, unknown>[];
  return { columns: cols, rows };
}

export async function updateRow(
  table: string,
  id: number,
  updates: Record<string, unknown>
): Promise<void> {
  if (!(await isAuthenticated())) throw new Error("Unauthorized");
  assertAllowedTable(table);

  const allowedCols = new Set(getColumns(table));
  const safeUpdates: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(updates)) {
    if (k !== "id" && allowedCols.has(k)) safeUpdates[k] = v;
  }

  if (Object.keys(safeUpdates).length === 0) return;

  const setClauses = Object.keys(safeUpdates)
    .map((k) => `"${k}" = ?`)
    .join(", ");
  const values = [...Object.values(safeUpdates), id];
  db.prepare(`UPDATE ${table} SET ${setClauses} WHERE id = ?`).run(...values);
}
