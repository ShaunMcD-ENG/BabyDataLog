import { redirect } from "next/navigation";
import { isAuthenticated } from "@/lib/auth";
import db from "@/lib/db/connection";
import { runMigrations } from "@/lib/db/schema";

runMigrations();

export default async function SyncPage() {
  if (!(await isAuthenticated())) redirect("/login");

  const conflicts = db.prepare(`
    SELECT * FROM sync_conflicts WHERE resolvedAtMs IS NULL ORDER BY createdAtMs DESC
  `).all() as Array<{
    id: number; table_name: string; syncUuid: string; deviceId: string;
    serverJson: string; deviceJson: string; createdAtMs: number;
  }>;

  const recentLog = db.prepare(`
    SELECT * FROM sync_log ORDER BY syncedAtMs DESC LIMIT 20
  `).all() as Array<{
    id: number; deviceId: string; table_name: string;
    syncUuid: string; action: string; syncedAtMs: number;
  }>;

  return (
    <main style={{ maxWidth: 800, margin: "0 auto", padding: "24px 16px" }}>
      <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 24 }}>Sync</h1>

      <section style={{ marginBottom: 32 }}>
        <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 12 }}>
          Pending Conflicts {conflicts.length > 0 && <span style={{ color: "#c00" }}>({conflicts.length})</span>}
        </h2>
        {conflicts.length === 0 ? (
          <p style={{ color: "#666" }}>No conflicts — all in sync.</p>
        ) : (
          conflicts.map(c => (
            <div key={c.id} style={{ background: "#fff", border: "1px solid #f0c040", borderRadius: 8, padding: 16, marginBottom: 12 }}>
              <p style={{ fontSize: 13, color: "#888", marginBottom: 8 }}>
                Table: <strong>{c.table_name}</strong> · Device: {c.deviceId} · {new Date(c.createdAtMs).toLocaleString()}
              </p>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, marginBottom: 12 }}>
                <div>
                  <p style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Server version</p>
                  <pre style={{ fontSize: 11, background: "#f5f5f5", padding: 8, borderRadius: 4, overflow: "auto" }}>
                    {JSON.stringify(JSON.parse(c.serverJson), null, 2)}
                  </pre>
                </div>
                <div>
                  <p style={{ fontSize: 12, fontWeight: 600, marginBottom: 4 }}>Device version</p>
                  <pre style={{ fontSize: 11, background: "#f5f5f5", padding: 8, borderRadius: 4, overflow: "auto" }}>
                    {JSON.stringify(JSON.parse(c.deviceJson), null, 2)}
                  </pre>
                </div>
              </div>
              <ConflictActions conflictId={c.id} />
            </div>
          ))
        )}
      </section>

      <section>
        <h2 style={{ fontSize: 16, fontWeight: 600, marginBottom: 12 }}>Recent Sync Activity</h2>
        {recentLog.length === 0 ? (
          <p style={{ color: "#666" }}>No sync activity yet.</p>
        ) : (
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr style={{ background: "#eee" }}>
                <th style={th}>Time</th>
                <th style={th}>Device</th>
                <th style={th}>Table</th>
                <th style={th}>Action</th>
              </tr>
            </thead>
            <tbody>
              {recentLog.map(row => (
                <tr key={row.id} style={{ borderBottom: "1px solid #eee" }}>
                  <td style={td}>{new Date(row.syncedAtMs).toLocaleString()}</td>
                  <td style={td}>{row.deviceId}</td>
                  <td style={td}>{row.table_name}</td>
                  <td style={td}>{row.action}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}

// Client component stub — will be fleshed out when building the sync UI
function ConflictActions({ conflictId }: { conflictId: number }) {
  return (
    <div style={{ display: "flex", gap: 8 }}>
      <form action={`/api/sync/resolve`} method="POST">
        <input type="hidden" name="conflictId" value={conflictId} />
        <input type="hidden" name="resolution" value="keep_server" />
        <button type="submit" style={{ padding: "6px 14px", background: "#4a90d9", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer", fontSize: 13 }}>
          Keep Server
        </button>
      </form>
      <form action={`/api/sync/resolve`} method="POST">
        <input type="hidden" name="conflictId" value={conflictId} />
        <input type="hidden" name="resolution" value="keep_device" />
        <button type="submit" style={{ padding: "6px 14px", background: "#e07000", color: "#fff", border: "none", borderRadius: 6, cursor: "pointer", fontSize: 13 }}>
          Keep Device
        </button>
      </form>
    </div>
  );
}

const th: React.CSSProperties = { padding: "8px 12px", textAlign: "left", fontSize: 13, fontWeight: 600 };
const td: React.CSSProperties = { padding: "8px 12px", fontSize: 14 };
