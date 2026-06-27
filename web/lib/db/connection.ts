import Database from "better-sqlite3";
import path from "path";
import fs from "fs";

const DB_PATH = process.env.DATABASE_PATH ?? "./data/babydatalog.db";
const resolved = path.resolve(DB_PATH);

// Ensure the data directory exists
fs.mkdirSync(path.dirname(resolved), { recursive: true });

// Single connection reused across requests (better-sqlite3 is synchronous)
const db = new Database(resolved);
db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");

export default db;
