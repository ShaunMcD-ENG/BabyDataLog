import { redirect } from "next/navigation";
import { isAuthenticated, isSetupComplete } from "@/lib/auth";
import Link from "next/link";
import DatabaseViewer from "./DatabaseViewer";

export const dynamic = "force-dynamic";

export default async function DatabasePage() {
  if (!isSetupComplete()) redirect("/setup");
  if (!(await isAuthenticated())) redirect("/login");

  return (
    <main
      style={{
        maxWidth: 1400,
        margin: "0 auto",
        padding: "32px 24px",
        fontFamily: "system-ui, sans-serif",
      }}
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 20,
          marginBottom: 28,
        }}
      >
        <Link
          href="/"
          style={{
            fontSize: 13,
            color: "#666",
            textDecoration: "none",
            display: "flex",
            alignItems: "center",
            gap: 4,
          }}
        >
          ← Dashboard
        </Link>
        <div>
          <h1
            style={{ fontSize: 20, fontWeight: 700, margin: 0, color: "#1a1a2e" }}
          >
            Database Viewer
          </h1>
          <p style={{ fontSize: 12, color: "#999", margin: "3px 0 0" }}>
            Select a table, set query options, then fetch. Click any row to edit.
            WHERE clause accepts raw SQL.
          </p>
        </div>
      </div>

      <DatabaseViewer />
    </main>
  );
}
