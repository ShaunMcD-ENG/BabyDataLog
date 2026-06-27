"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";

export default function LoginPage() {
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const router = useRouter();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");

    const res = await fetch("/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ password }),
    });

    if (res.ok) {
      router.push("/feedings");
    } else {
      setError("Incorrect password");
      setLoading(false);
    }
  }

  return (
    <main style={{ display: "flex", minHeight: "100vh", alignItems: "center", justifyContent: "center" }}>
      <div style={{ width: 320, padding: 32, background: "#fff", borderRadius: 12, boxShadow: "0 2px 12px rgba(0,0,0,0.1)" }}>
        <h1 style={{ marginBottom: 8, fontSize: 24, fontWeight: 700 }}>BabyDataLog</h1>
        <p style={{ marginBottom: 24, color: "#666", fontSize: 14 }}>Enter the admin password to continue</p>
        <form onSubmit={handleSubmit}>
          <input
            type="password"
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="Password"
            autoFocus
            required
            style={{ width: "100%", padding: "10px 12px", border: "1px solid #ddd", borderRadius: 8, fontSize: 16, marginBottom: 12 }}
          />
          {error && <p style={{ color: "#c00", fontSize: 13, marginBottom: 12 }}>{error}</p>}
          <button
            type="submit"
            disabled={loading}
            style={{ width: "100%", padding: "10px", background: "#4a90d9", color: "#fff", border: "none", borderRadius: 8, fontSize: 16, cursor: "pointer" }}
          >
            {loading ? "Checking…" : "Log in"}
          </button>
        </form>
      </div>
    </main>
  );
}
