import { redirect } from "next/navigation";
import { isAuthenticated, isSetupComplete, getSession } from "@/lib/auth";
import { cookies } from "next/headers";
import db from "@/lib/db/connection";

// ── Server Actions ─────────────────────────────────────────────────────────

async function approveDevice(formData: FormData) {
  "use server";
  if (!(await isAuthenticated())) redirect("/login");
  const deviceId = formData.get("deviceId") as string;
  const apiKey =
    crypto.randomUUID().replace(/-/g, "") +
    crypto.randomUUID().replace(/-/g, "");
  db.prepare(
    `UPDATE devices SET status = 'approved', apiKey = ?, approvedAtMs = ? WHERE deviceId = ?`
  ).run(apiKey, Date.now(), deviceId);
  redirect("/");
}

async function rejectDevice(formData: FormData) {
  "use server";
  if (!(await isAuthenticated())) redirect("/login");
  db.prepare("UPDATE devices SET status = 'rejected' WHERE deviceId = ?").run(
    formData.get("deviceId") as string
  );
  redirect("/");
}

async function revokeDevice(formData: FormData) {
  "use server";
  if (!(await isAuthenticated())) redirect("/login");
  db.prepare(
    "UPDATE devices SET status = 'rejected', apiKey = NULL WHERE deviceId = ?"
  ).run(formData.get("deviceId") as string);
  redirect("/");
}

async function removeDevice(formData: FormData) {
  "use server";
  if (!(await isAuthenticated())) redirect("/login");
  db.prepare("DELETE FROM devices WHERE deviceId = ?").run(
    formData.get("deviceId") as string
  );
  redirect("/");
}

async function resolveConflict(formData: FormData) {
  "use server";
  if (!(await isAuthenticated())) redirect("/login");
  const conflictId = Number(formData.get("conflictId"));
  const resolution = formData.get("resolution") as "keep_server" | "keep_device";

  const conflict = db
    .prepare(`SELECT * FROM sync_conflicts WHERE id = ? AND resolvedAtMs IS NULL`)
    .get(conflictId) as {
      table_name: string; syncUuid: string;
      serverJson: string; deviceJson: string;
    } | undefined;

  if (conflict && resolution === "keep_device") {
    const record = JSON.parse(conflict.deviceJson);
    const cols = Object.keys(record);
    const assignments = cols.map((c) => `${c} = ?`).join(", ");
    db.prepare(
      `UPDATE ${conflict.table_name} SET ${assignments} WHERE syncUuid = ?`
    ).run(...cols.map((c) => record[c]), record.syncUuid);
  }

  db.prepare(
    `UPDATE sync_conflicts SET resolvedAtMs = ?, resolution = ? WHERE id = ?`
  ).run(Date.now(), resolution, conflictId);
  redirect("/");
}

async function logout() {
  "use server";
  const session = await getSession(await cookies());
  session.destroy();
  redirect("/login");
}

// ── Types ──────────────────────────────────────────────────────────────────

type Device = {
  id: number; deviceId: string; name: string; pairingCode: string;
  status: string; apiKey: string | null;
  registeredAtMs: number; approvedAtMs: number | null;
};

type LogEntry = {
  id: number; deviceId: string; table_name: string;
  action: string; syncedAtMs: number; deviceName: string | null;
};

type Conflict = {
  id: number; table_name: string; syncUuid: string; deviceId: string;
  serverJson: string; deviceJson: string; createdAtMs: number;
};

// ── Helpers ────────────────────────────────────────────────────────────────

function timeAgo(ms: number | null): string {
  if (!ms) return "never";
  const diff = Math.floor((Date.now() - ms) / 60000);
  if (diff < 1) return "just now";
  if (diff < 60) return `${diff}m ago`;
  const h = Math.floor(diff / 60);
  if (h < 24) return `${h}h ago`;
  return `${Math.floor(h / 24)}d ago`;
}

// ── Page ───────────────────────────────────────────────────────────────────

export default async function DashboardPage() {
  if (!isSetupComplete()) redirect("/setup");
  if (!(await isAuthenticated())) redirect("/login");

  const devices = db
    .prepare("SELECT * FROM devices ORDER BY registeredAtMs DESC")
    .all() as Device[];

  const pending = devices.filter((d) => d.status === "pending");
  const approved = devices.filter((d) => d.status === "approved");
  const rejected = devices.filter((d) => d.status === "rejected");

  const conflicts = db
    .prepare(
      `SELECT * FROM sync_conflicts WHERE resolvedAtMs IS NULL ORDER BY createdAtMs DESC`
    )
    .all() as Conflict[];

  const recentLog = db
    .prepare(
      `SELECT sl.*, d.name AS deviceName
       FROM sync_log sl
       LEFT JOIN devices d ON d.deviceId = sl.deviceId
       ORDER BY sl.syncedAtMs DESC LIMIT 30`
    )
    .all() as LogEntry[];

  // Last sync time per approved device
  const lastSync: Record<string, number | null> = {};
  for (const d of approved) {
    const row = db
      .prepare(`SELECT MAX(syncedAtMs) as t FROM sync_log WHERE deviceId = ?`)
      .get(d.deviceId) as { t: number | null };
    lastSync[d.deviceId] = row.t;
  }

  return (
    <main style={{ maxWidth: 820, margin: "0 auto", padding: "32px 24px", fontFamily: "system-ui, sans-serif" }}>

      {/* ── Header ── */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 36 }}>
        <div>
          <h1 style={{ fontSize: 22, fontWeight: 700, color: "#1a1a2e", margin: 0 }}>BabyDataLog</h1>
          <p style={{ fontSize: 13, color: "#888", marginTop: 3 }}>Sync Server</p>
        </div>
        <form action={logout}>
          <button type="submit" style={s.btnGhost}>Log out</button>
        </form>
      </div>

      {/* ── Pending Devices ── */}
      {pending.length > 0 && (
        <section style={{ ...s.card, borderColor: "#e0b84a", marginBottom: 20 }}>
          <h2 style={{ ...s.heading, color: "#856404" }}>
            ⏳ Pending Approval ({pending.length})
          </h2>
          <p style={{ fontSize: 13, color: "#666", margin: "4px 0 16px" }}>
            Verify the pairing code matches what&apos;s shown on the phone before approving.
          </p>
          {pending.map((d) => (
            <div
              key={d.deviceId}
              style={{ display: "flex", alignItems: "center", justifyContent: "space-between", padding: "14px 0", borderTop: "1px solid #f5e8c0" }}
            >
              <div>
                <div style={{ fontWeight: 600, fontSize: 15 }}>{d.name}</div>
                <div style={{ fontSize: 13, color: "#888", marginTop: 4 }}>
                  Pairing code:{" "}
                  <span style={{ fontFamily: "monospace", fontSize: 18, fontWeight: 700, letterSpacing: 3, color: "#1a1a2e" }}>
                    {d.pairingCode}
                  </span>
                </div>
                <div style={{ fontSize: 12, color: "#aaa", marginTop: 3 }}>
                  Requested {timeAgo(d.registeredAtMs)}
                </div>
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <form action={approveDevice}>
                  <input type="hidden" name="deviceId" value={d.deviceId} />
                  <button type="submit" style={s.btnGreen}>✓ Approve</button>
                </form>
                <form action={rejectDevice}>
                  <input type="hidden" name="deviceId" value={d.deviceId} />
                  <button type="submit" style={s.btnRed}>✗ Reject</button>
                </form>
              </div>
            </div>
          ))}
        </section>
      )}

      {/* ── Connected Devices ── */}
      <section style={{ ...s.card, marginBottom: 20 }}>
        <h2 style={s.heading}>📱 Connected Devices</h2>
        {approved.length === 0 ? (
          <p style={{ fontSize: 14, color: "#888", marginTop: 8 }}>
            No devices connected yet. Open the BabyDataLog app on your phone, go to the Sync tab, and enter this server&apos;s address to get started.
          </p>
        ) : (
          <table style={s.table}>
            <thead>
              <tr>
                {["Device Name", "Approved", "Last Sync", ""].map((h) => (
                  <th key={h} style={s.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {approved.map((d) => (
                <tr key={d.deviceId}>
                  <td style={{ ...s.td, fontWeight: 600 }}>{d.name}</td>
                  <td style={s.td}>{timeAgo(d.approvedAtMs)}</td>
                  <td style={s.td}>{timeAgo(lastSync[d.deviceId])}</td>
                  <td style={s.td}>
                    <form action={revokeDevice}>
                      <input type="hidden" name="deviceId" value={d.deviceId} />
                      <button type="submit" style={s.btnSmallRed}>Revoke</button>
                    </form>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {/* ── Conflicts ── */}
      {conflicts.length > 0 && (
        <section style={{ ...s.card, borderColor: "#f5c6c6", marginBottom: 20 }}>
          <h2 style={{ ...s.heading, color: "#c62828" }}>
            ⚠️ Sync Conflicts ({conflicts.length})
          </h2>
          <p style={{ fontSize: 13, color: "#666", margin: "4px 0 16px" }}>
            The same record was edited on the server and on a device. Choose which version to keep.
          </p>
          {conflicts.map((c) => (
            <div key={c.id} style={{ borderTop: "1px solid #f5c6c6", paddingTop: 16, marginTop: 16 }}>
              <p style={{ fontSize: 12, color: "#888", marginBottom: 10 }}>
                Table: <code style={{ background: "#f5f5f5", padding: "1px 5px", borderRadius: 3 }}>{c.table_name}</code>
                {" · "}Device: {c.deviceId.slice(0, 8)}…
                {" · "}{timeAgo(c.createdAtMs)}
              </p>
              <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10, marginBottom: 12 }}>
                {[
                  { label: "Server version", json: c.serverJson },
                  { label: "Device version", json: c.deviceJson },
                ].map(({ label, json }) => (
                  <div key={label}>
                    <p style={{ fontSize: 12, fontWeight: 600, color: "#555", marginBottom: 4 }}>{label}</p>
                    <pre style={{ fontSize: 11, background: "#f8f8f8", padding: 10, borderRadius: 6, overflow: "auto", maxHeight: 160, lineHeight: 1.4 }}>
                      {JSON.stringify(JSON.parse(json), null, 2)}
                    </pre>
                  </div>
                ))}
              </div>
              <div style={{ display: "flex", gap: 8 }}>
                <form action={resolveConflict}>
                  <input type="hidden" name="conflictId" value={c.id} />
                  <input type="hidden" name="resolution" value="keep_server" />
                  <button type="submit" style={s.btnBlue}>Keep Server</button>
                </form>
                <form action={resolveConflict}>
                  <input type="hidden" name="conflictId" value={c.id} />
                  <input type="hidden" name="resolution" value="keep_device" />
                  <button type="submit" style={s.btnOrange}>Keep Device</button>
                </form>
              </div>
            </div>
          ))}
        </section>
      )}

      {/* ── Rejected Devices ── */}
      {rejected.length > 0 && (
        <section style={{ ...s.card, marginBottom: 20 }}>
          <h2 style={{ ...s.heading, color: "#999" }}>🚫 Rejected Devices</h2>
          {rejected.map((d) => (
            <div
              key={d.deviceId}
              style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "8px 0", borderTop: "1px solid #f0f0f0", marginTop: 8 }}
            >
              <span style={{ fontSize: 14, color: "#888" }}>{d.name}</span>
              <form action={removeDevice}>
                <input type="hidden" name="deviceId" value={d.deviceId} />
                <button type="submit" style={s.btnGhost}>Remove</button>
              </form>
            </div>
          ))}
        </section>
      )}

      {/* ── Recent Activity ── */}
      <section style={s.card}>
        <h2 style={s.heading}>📋 Recent Sync Activity</h2>
        {recentLog.length === 0 ? (
          <p style={{ fontSize: 14, color: "#888", marginTop: 8 }}>No sync activity yet.</p>
        ) : (
          <table style={s.table}>
            <thead>
              <tr>
                {["Time", "Device", "Table", "Action"].map((h) => (
                  <th key={h} style={s.th}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {recentLog.map((row) => (
                <tr key={row.id}>
                  <td style={s.td}>
                    {new Date(row.syncedAtMs).toLocaleString("en-GB", {
                      dateStyle: "short", timeStyle: "short",
                    })}
                  </td>
                  <td style={s.td}>{row.deviceName ?? row.deviceId.slice(0, 8) + "…"}</td>
                  <td style={s.td}>
                    <code style={{ fontSize: 12 }}>{row.table_name}</code>
                  </td>
                  <td style={s.td}>
                    <span style={{
                      display: "inline-block", padding: "2px 8px", borderRadius: 12,
                      fontSize: 11.5, fontWeight: 600,
                      background: row.action === "push" ? "#e8f0fe" : row.action === "conflict" ? "#fdecea" : "#e6f4ea",
                      color: row.action === "push" ? "#1a5cb4" : row.action === "conflict" ? "#c62828" : "#2e7d32",
                    }}>
                      {row.action}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </main>
  );
}

// ── Style constants ────────────────────────────────────────────────────────

const base: React.CSSProperties = {
  border: "none", borderRadius: 6, cursor: "pointer",
  fontSize: 13, fontWeight: 500, padding: "7px 14px", fontFamily: "inherit",
};

const s = {
  card: {
    background: "#fff",
    border: "1px solid #e2e4e8",
    borderRadius: 10,
    padding: "20px 22px",
  } as React.CSSProperties,
  heading: {
    fontSize: 15, fontWeight: 600, margin: 0,
  } as React.CSSProperties,
  table: { width: "100%", borderCollapse: "collapse", marginTop: 12 } as React.CSSProperties,
  th: {
    padding: "8px 12px", textAlign: "left" as const, fontSize: 11, fontWeight: 600,
    color: "#777", textTransform: "uppercase" as const, letterSpacing: "0.05em",
    background: "#f8f9fb", borderBottom: "1px solid #e2e4e8",
  } as React.CSSProperties,
  td: { padding: "10px 12px", fontSize: 13.5, borderBottom: "1px solid #f0f0f0" } as React.CSSProperties,
  btnGhost: { ...base, background: "transparent", border: "1px solid #d0d3d8", color: "#555" } as React.CSSProperties,
  btnGreen: { ...base, background: "#2e7d32", color: "#fff" } as React.CSSProperties,
  btnRed:   { ...base, background: "#c62828", color: "#fff" } as React.CSSProperties,
  btnBlue:  { ...base, background: "#1a5cb4", color: "#fff" } as React.CSSProperties,
  btnOrange:{ ...base, background: "#e07000", color: "#fff" } as React.CSSProperties,
  btnSmallRed: { ...base, padding: "4px 10px", fontSize: 12, background: "#fdecea", color: "#c62828", border: "1px solid #f5c6c6" } as React.CSSProperties,
};
