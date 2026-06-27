import { redirect } from "next/navigation";
import { isAuthenticated } from "@/lib/auth";
import db from "@/lib/db/connection";
import { runMigrations } from "@/lib/db/schema";

runMigrations();

export default async function FeedingsPage() {
  if (!(await isAuthenticated())) redirect("/login");

  const feedings = db.prepare(`
    SELECT f.*, b.name AS babyName
    FROM feeding_sessions f
    JOIN babies b ON b.id = f.babyId
    ORDER BY f.startTimeMs DESC
    LIMIT 100
  `).all() as Array<{
    id: number; babyName: string; startTimeMs: number;
    durationMinutes: number | null; breastSide: string; notes: string | null;
  }>;

  return (
    <main style={{ maxWidth: 800, margin: "0 auto", padding: "24px 16px" }}>
      <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 16 }}>Feedings</h1>
      {feedings.length === 0 ? (
        <p style={{ color: "#666" }}>No feedings recorded yet.</p>
      ) : (
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead>
            <tr style={{ background: "#eee" }}>
              <th style={th}>Baby</th>
              <th style={th}>Date/Time</th>
              <th style={th}>Side</th>
              <th style={th}>Duration</th>
              <th style={th}>Notes</th>
            </tr>
          </thead>
          <tbody>
            {feedings.map(f => (
              <tr key={f.id} style={{ borderBottom: "1px solid #eee" }}>
                <td style={td}>{f.babyName}</td>
                <td style={td}>{new Date(f.startTimeMs).toLocaleString()}</td>
                <td style={td}>{f.breastSide}</td>
                <td style={td}>{f.durationMinutes != null ? `${f.durationMinutes.toFixed(1)} min` : "—"}</td>
                <td style={td}>{f.notes ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}

const th: React.CSSProperties = { padding: "8px 12px", textAlign: "left", fontSize: 13, fontWeight: 600 };
const td: React.CSSProperties = { padding: "8px 12px", fontSize: 14 };
