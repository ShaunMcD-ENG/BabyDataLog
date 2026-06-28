"use client";
import { useState, useTransition } from "react";
import { fetchRows, updateRow } from "./actions";

const TABLES = [
  "babies",
  "feeding_sessions",
  "nappy_changes",
  "milestones",
  "growth_measurements",
  "sync_log",
  "devices",
];

type FetchResult = { columns: string[]; rows: Record<string, unknown>[] };

export default function DatabaseViewer() {
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [limit, setLimit] = useState(100);
  const [orderBy, setOrderBy] = useState("id");
  const [orderDir, setOrderDir] = useState<"ASC" | "DESC">("DESC");
  const [where, setWhere] = useState("");
  const [results, setResults] = useState<FetchResult | null>(null);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [editRow, setEditRow] = useState<Record<string, unknown> | null>(null);
  const [editFields, setEditFields] = useState<Record<string, string | null>>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  function handleTableClick(table: string) {
    setSelectedTable(table);
    setResults(null);
    setFetchError(null);
    setOrderBy("id");
    setWhere("");
  }

  function handleFetch() {
    if (!selectedTable) return;
    setFetchError(null);
    startTransition(async () => {
      try {
        const result = await fetchRows(selectedTable, limit, orderBy, orderDir, where);
        setResults(result);
      } catch (e) {
        setFetchError(e instanceof Error ? e.message : String(e));
        setResults(null);
      }
    });
  }

  function handleRowClick(row: Record<string, unknown>) {
    const fields: Record<string, string | null> = {};
    for (const [k, v] of Object.entries(row)) {
      fields[k] = v === null ? null : String(v);
    }
    setEditRow(row);
    setEditFields(fields);
    setSaveError(null);
  }

  function handleFieldChange(col: string, value: string | null) {
    setEditFields((prev) => ({ ...prev, [col]: value }));
  }

  function handleSave() {
    if (!editRow || !selectedTable) return;
    const updates: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(editFields)) {
      if (k === "id") continue;
      if (v === null) {
        updates[k] = null;
      } else if (typeof editRow[k] === "number") {
        updates[k] = v === "" ? null : Number(v);
      } else {
        updates[k] = v === "" ? null : v;
      }
    }
    setSaveError(null);
    startTransition(async () => {
      try {
        await updateRow(selectedTable, Number(editRow.id), updates);
        setEditRow(null);
        const result = await fetchRows(selectedTable, limit, orderBy, orderDir, where);
        setResults(result);
      } catch (e) {
        setSaveError(e instanceof Error ? e.message : String(e));
      }
    });
  }

  return (
    <div>
      {/* Table selector */}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 24 }}>
        {TABLES.map((t) => (
          <button
            key={t}
            onClick={() => handleTableClick(t)}
            style={{
              padding: "8px 16px",
              borderRadius: 6,
              border: "1px solid",
              borderColor: selectedTable === t ? "#1a73e8" : "#d0d3d8",
              background: selectedTable === t ? "#e8f0fe" : "#fff",
              color: selectedTable === t ? "#1a73e8" : "#444",
              fontWeight: selectedTable === t ? 600 : 400,
              cursor: "pointer",
              fontSize: 13,
              fontFamily: "monospace",
            }}
          >
            {t}
          </button>
        ))}
      </div>

      {selectedTable && (
        <>
          {/* Query options */}
          <div
            style={{
              background: "#f8f9fb",
              border: "1px solid #e2e4e8",
              borderRadius: 8,
              padding: "16px 20px",
              marginBottom: 16,
            }}
          >
            <div style={{ display: "flex", gap: 16, flexWrap: "wrap", alignItems: "flex-end" }}>
              <label style={labelStyle}>
                Limit
                <input
                  type="number"
                  value={limit}
                  onChange={(e) => setLimit(Number(e.target.value))}
                  min={1}
                  max={1000}
                  style={{ ...inputStyle, width: 80 }}
                />
              </label>
              <label style={labelStyle}>
                Order by
                <input
                  type="text"
                  value={orderBy}
                  onChange={(e) => setOrderBy(e.target.value)}
                  style={{ ...inputStyle, width: 140 }}
                />
              </label>
              <label style={labelStyle}>
                Direction
                <select
                  value={orderDir}
                  onChange={(e) => setOrderDir(e.target.value as "ASC" | "DESC")}
                  style={{ ...inputStyle, width: 90 }}
                >
                  <option value="DESC">DESC</option>
                  <option value="ASC">ASC</option>
                </select>
              </label>
              <label style={{ ...labelStyle, flex: 1, minWidth: 220 }}>
                WHERE clause (raw SQL)
                <input
                  type="text"
                  value={where}
                  onChange={(e) => setWhere(e.target.value)}
                  placeholder="e.g. deletedAtMs IS NOT NULL"
                  style={{ ...inputStyle, width: "100%" }}
                />
              </label>
            </div>
            <div style={{ display: "flex", gap: 8, marginTop: 12 }}>
              <button onClick={handleFetch} disabled={isPending} style={btnPrimary}>
                {isPending ? "Fetching…" : "Fetch"}
              </button>
              <button
                onClick={() => { setResults(null); setFetchError(null); }}
                style={btnGhost}
              >
                Clear Results
              </button>
            </div>
          </div>

          {fetchError && (
            <div
              style={{
                background: "#fdecea",
                border: "1px solid #f5c6c6",
                borderRadius: 6,
                padding: "12px 16px",
                marginBottom: 16,
                color: "#c62828",
                fontSize: 13,
                fontFamily: "monospace",
              }}
            >
              {fetchError}
            </div>
          )}

          {results && (
            <div>
              <div style={{ marginBottom: 8, fontSize: 13, color: "#888" }}>
                {results.rows.length} row{results.rows.length !== 1 ? "s" : ""} — click a row to edit
              </div>
              <div style={{ overflowX: "auto", borderRadius: 8, border: "1px solid #e2e4e8" }}>
                <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12.5 }}>
                  <thead>
                    <tr>
                      {results.columns.map((col) => (
                        <th
                          key={col}
                          style={{
                            padding: "9px 12px",
                            textAlign: "left",
                            background: "#f0f2f5",
                            borderBottom: "2px solid #d0d3d8",
                            whiteSpace: "nowrap",
                            fontFamily: "monospace",
                            fontSize: 11.5,
                            fontWeight: 700,
                            color: "#555",
                          }}
                        >
                          {col}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {results.rows.map((row, i) => (
                      <ResultRow
                        key={i}
                        row={row}
                        columns={results.columns}
                        even={i % 2 === 0}
                        onClick={() => handleRowClick(row)}
                      />
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </>
      )}

      {/* Edit modal */}
      {editRow && (
        <div
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.45)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 100,
            padding: 24,
          }}
          onClick={(e) => {
            if (e.target === e.currentTarget) setEditRow(null);
          }}
        >
          <div
            style={{
              background: "#fff",
              borderRadius: 12,
              padding: "28px 32px",
              maxWidth: 720,
              width: "100%",
              maxHeight: "85vh",
              overflowY: "auto",
              boxShadow: "0 8px 40px rgba(0,0,0,0.18)",
            }}
          >
            <h2 style={{ margin: "0 0 6px", fontSize: 17, fontWeight: 600 }}>
              Edit row
            </h2>
            <p style={{ margin: "0 0 20px", fontSize: 12.5, color: "#888", fontFamily: "monospace" }}>
              {selectedTable} · id = {String(editRow.id)}
            </p>

            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(280px, 1fr))",
                gap: "16px 24px",
              }}
            >
              {Object.entries(editFields).map(([col, val]) => (
                <div key={col} style={{ display: "flex", flexDirection: "column", gap: 5 }}>
                  <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                    <label
                      style={{
                        fontSize: 11,
                        fontWeight: 700,
                        color: "#555",
                        fontFamily: "monospace",
                        textTransform: "uppercase",
                        letterSpacing: "0.04em",
                      }}
                    >
                      {col}
                    </label>
                    {col === "deletedAtMs" && val !== null && (
                      <span
                        style={{
                          background: "#fdecea",
                          color: "#c62828",
                          fontSize: 10,
                          padding: "1px 6px",
                          borderRadius: 4,
                          fontWeight: 600,
                        }}
                      >
                        DELETED
                      </span>
                    )}
                  </div>

                  {col === "id" ? (
                    <input
                      value={val ?? ""}
                      disabled
                      style={{ ...inputStyle, background: "#f5f5f5", color: "#aaa" }}
                    />
                  ) : val === null ? (
                    <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                      <span
                        style={{
                          flex: 1,
                          padding: "7px 10px",
                          border: "1px solid #e8e8e8",
                          borderRadius: 6,
                          background: "#fafafa",
                          color: "#bbb",
                          fontFamily: "monospace",
                          fontSize: 12.5,
                        }}
                      >
                        NULL
                      </span>
                      <button
                        onClick={() => handleFieldChange(col, "")}
                        style={btnSmall}
                      >
                        Set value
                      </button>
                    </div>
                  ) : (
                    <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                      <input
                        value={val}
                        onChange={(e) => handleFieldChange(col, e.target.value)}
                        style={{ ...inputStyle, flex: 1, minWidth: 0 }}
                      />
                      <button
                        onClick={() => handleFieldChange(col, null)}
                        style={{
                          ...btnSmall,
                          background: "#fdecea",
                          color: "#c62828",
                          borderColor: "#f5c6c6",
                        }}
                        title="Set to NULL"
                      >
                        NULL
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>

            {saveError && (
              <div
                style={{
                  marginTop: 16,
                  padding: "10px 14px",
                  background: "#fdecea",
                  border: "1px solid #f5c6c6",
                  borderRadius: 6,
                  color: "#c62828",
                  fontSize: 13,
                  fontFamily: "monospace",
                }}
              >
                {saveError}
              </div>
            )}

            <div style={{ display: "flex", gap: 10, marginTop: 24 }}>
              <button onClick={handleSave} disabled={isPending} style={btnPrimary}>
                {isPending ? "Saving…" : "Save Changes"}
              </button>
              <button onClick={() => setEditRow(null)} style={btnGhost}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function ResultRow({
  row,
  columns,
  even,
  onClick,
}: {
  row: Record<string, unknown>;
  columns: string[];
  even: boolean;
  onClick: () => void;
}) {
  const [hovered, setHovered] = useState(false);
  const isDeleted = row.deletedAtMs !== undefined && row.deletedAtMs !== null;

  return (
    <tr
      onClick={onClick}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      style={{
        cursor: "pointer",
        background: hovered ? "#e8f4fd" : isDeleted ? "#fff8f8" : even ? "#fff" : "#fafafa",
        borderBottom: "1px solid #f0f0f0",
      }}
    >
      {columns.map((col) => {
        const val = row[col];
        const isNull = val === null;
        const isDeletedCol = col === "deletedAtMs" && val !== null;
        return (
          <td
            key={col}
            style={{
              padding: "7px 12px",
              maxWidth: 220,
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
              color: isNull ? "#ccc" : isDeletedCol ? "#c62828" : "#222",
              fontFamily:
                typeof val === "number" ||
                col.endsWith("Id") ||
                col === "syncUuid" ||
                col === "deviceId" ||
                col === "apiKey"
                  ? "monospace"
                  : "inherit",
              fontSize: 12.5,
            }}
            title={isNull ? "NULL" : String(val)}
          >
            {isNull ? "NULL" : String(val)}
          </td>
        );
      })}
    </tr>
  );
}

const labelStyle: React.CSSProperties = {
  display: "flex",
  flexDirection: "column",
  gap: 4,
  fontSize: 12,
  fontWeight: 600,
  color: "#555",
};

const inputStyle: React.CSSProperties = {
  padding: "7px 10px",
  border: "1px solid #d0d3d8",
  borderRadius: 6,
  fontSize: 13,
  fontFamily: "inherit",
  background: "#fff",
  outline: "none",
};

const base: React.CSSProperties = {
  border: "none",
  borderRadius: 6,
  cursor: "pointer",
  fontSize: 13,
  fontWeight: 500,
  padding: "8px 16px",
  fontFamily: "inherit",
};

const btnPrimary: React.CSSProperties = {
  ...base,
  background: "#1a73e8",
  color: "#fff",
};

const btnGhost: React.CSSProperties = {
  ...base,
  background: "transparent",
  border: "1px solid #d0d3d8",
  color: "#555",
};

const btnSmall: React.CSSProperties = {
  padding: "4px 10px",
  fontSize: 12,
  fontWeight: 500,
  fontFamily: "inherit",
  borderRadius: 5,
  border: "1px solid #d0d3d8",
  background: "#f8f9fb",
  color: "#555",
  cursor: "pointer",
  whiteSpace: "nowrap" as const,
};
