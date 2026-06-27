"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

export default function SetupForm() {
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (password !== confirm) { setError("Passwords don't match"); return; }
    if (password.length < 8) { setError("Password must be at least 8 characters"); return; }
    setLoading(true);
    setError("");
    const res = await fetch("/api/auth/setup", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password }),
    });
    if (res.ok) {
      router.push("/");
    } else {
      const data = await res.json();
      setError(data.error ?? "Setup failed");
      setLoading(false);
    }
  }

  return (
    <main style={pageStyle}>
      <div style={boxStyle}>
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>BabyDataLog</h1>
        <p style={{ fontSize: 15, fontWeight: 600, color: "#1a5cb4", marginBottom: 6 }}>First-time Setup</p>
        <p style={{ fontSize: 13, color: "#666", marginBottom: 28, lineHeight: 1.5 }}>
          Create an admin password to protect your sync server.
          You&apos;ll need this to log in and approve devices.
        </p>
        <form onSubmit={handleSubmit}>
          <div style={{ marginBottom: 12 }}>
            <label style={labelStyle}>Admin Password</label>
            <input
              type="password" value={password}
              onChange={(e) => setPassword(e.target.value)}
              required minLength={8} autoFocus
              placeholder="Min. 8 characters"
              style={inputStyle}
            />
          </div>
          <div style={{ marginBottom: 20 }}>
            <label style={labelStyle}>Confirm Password</label>
            <input
              type="password" value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              required placeholder="Repeat password"
              style={inputStyle}
            />
          </div>
          {error && <p style={{ color: "#c62828", fontSize: 13, marginBottom: 14 }}>{error}</p>}
          <button type="submit" disabled={loading} style={btnStyle}>
            {loading ? "Setting up…" : "Set Password & Continue →"}
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
  borderRadius: 8, fontSize: 14, fontFamily: "inherit",
};
const btnStyle: React.CSSProperties = {
  width: "100%", padding: "11px", background: "#4a90d9", color: "#fff",
  border: "none", borderRadius: 8, fontSize: 14, cursor: "pointer", fontFamily: "inherit",
};
