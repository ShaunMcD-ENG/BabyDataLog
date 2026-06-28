"use client";

import { useTransition } from "react";

export default function ClearDatabaseButton({
  clearAction,
}: {
  clearAction: () => Promise<void>;
}) {
  const [pending, startTransition] = useTransition();

  function handleClick() {
    if (
      !window.confirm(
        "Are you sure?\n\nThis will permanently delete ALL baby data, sync history, and disconnect all devices.\n\nThis cannot be undone."
      )
    )
      return;
    startTransition(() => clearAction());
  }

  return (
    <button
      onClick={handleClick}
      disabled={pending}
      style={{
        border: "none",
        borderRadius: 6,
        cursor: pending ? "not-allowed" : "pointer",
        fontSize: 13,
        fontWeight: 500,
        padding: "7px 14px",
        fontFamily: "inherit",
        background: pending ? "#f5c6c6" : "#c62828",
        color: "#fff",
        opacity: pending ? 0.7 : 1,
      }}
    >
      {pending ? "Clearing…" : "Clear All Data"}
    </button>
  );
}
