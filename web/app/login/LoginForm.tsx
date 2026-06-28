"use client";

import { useActionState } from "react";

type LoginAction = (prevState: string, formData: FormData) => Promise<string>;

export default function LoginForm({ loginAction }: { loginAction: LoginAction }) {
  const [error, formAction, isPending] = useActionState(loginAction, "");

  return (
    <main style={pageStyle}>
      <div style={boxStyle}>
        <h1 style={{ fontSize: 22, fontWeight: 700, marginBottom: 4 }}>BabyDataLog</h1>
        <p style={{ fontSize: 14, color: "#666", marginBottom: 28 }}>Sync Server · Admin Login</p>
        <form action={formAction}>
          <input
            type="password"
            name="password"
            placeholder="Admin password"
            autoFocus
            required
            style={inputStyle}
          />
          {error && <p style={{ color: "#c62828", fontSize: 13, marginBottom: 12 }}>{error}</p>}
          <button type="submit" disabled={isPending} style={btnStyle}>
            {isPending ? "Checking…" : "Log in"}
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
  width: 340, padding: 36, background: "#fff",
  borderRadius: 12, boxShadow: "0 2px 20px rgba(0,0,0,0.1)",
};
const inputStyle: React.CSSProperties = {
  width: "100%", padding: "10px 12px", border: "1px solid #d0d3d8",
  borderRadius: 8, fontSize: 15, marginBottom: 12, fontFamily: "inherit",
  boxSizing: "border-box",
};
const btnStyle: React.CSSProperties = {
  width: "100%", padding: "11px", background: "#4a90d9", color: "#fff",
  border: "none", borderRadius: 8, fontSize: 15, cursor: "pointer", fontFamily: "inherit",
};
