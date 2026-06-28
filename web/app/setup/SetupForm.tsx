"use client";

import { useActionState } from "react";

type SetupAction = (prevState: string, formData: FormData) => Promise<string>;

export default function SetupForm({ setupAction }: { setupAction: SetupAction }) {
  const [error, formAction, isPending] = useActionState(setupAction, "");

  return (
    <main style={pageStyle}>
      <div style={boxStyle}>
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>BabyDataLog</h1>
        <p style={{ fontSize: 15, fontWeight: 600, color: "#1a5cb4", marginBottom: 6 }}>First-time Setup</p>
        <p style={{ fontSize: 13, color: "#666", marginBottom: 28, lineHeight: 1.5 }}>
          Create an admin password to protect your sync server.
          You&apos;ll need this to log in and approve devices.
        </p>
        <form action={formAction}>
          <div style={{ marginBottom: 12 }}>
            <label style={labelStyle}>Admin Password</label>
            <input
              type="password"
              name="password"
              required
              minLength={8}
              autoFocus
              placeholder="Min. 8 characters"
              style={inputStyle}
            />
          </div>
          <div style={{ marginBottom: 20 }}>
            <label style={labelStyle}>Confirm Password</label>
            <input
              type="password"
              name="confirm"
              required
              placeholder="Repeat password"
              style={inputStyle}
            />
          </div>
          {error && <p style={{ color: "#c62828", fontSize: 13, marginBottom: 14 }}>{error}</p>}
          <button type="submit" disabled={isPending} style={btnStyle}>
            {isPending ? "Setting up…" : "Set Password & Continue →"}
          </button>
        </form>
      </div>
    </main>
  );
}

const pageStyle: React.CSSProperties = {
  display: "flex", minHeight: "100vh",
  alignItems: "center", justifyContent: "center", background: "#f0f2f5",
};
const boxStyle: React.CSSProperties = {
  width: 360, padding: 36, background: "#fff",
  borderRadius: 12, boxShadow: "0 2px 20px rgba(0,0,0,0.1)",
};
const labelStyle: React.CSSProperties = {
  display: "block", fontSize: 12, fontWeight: 600, color: "#555",
  marginBottom: 5, textTransform: "uppercase", letterSpacing: "0.04em",
};
const inputStyle: React.CSSProperties = {
  width: "100%", padding: "10px 12px", border: "1px solid #d0d3d8",
  borderRadius: 8, fontSize: 14, fontFamily: "inherit", boxSizing: "border-box",
};
const btnStyle: React.CSSProperties = {
  width: "100%", padding: "11px", background: "#4a90d9", color: "#fff",
  border: "none", borderRadius: 8, fontSize: 14, cursor: "pointer", fontFamily: "inherit",
};
