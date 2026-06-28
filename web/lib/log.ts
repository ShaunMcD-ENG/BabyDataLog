type Level = "INFO" | "WARN" | "ERROR";

function log(level: Level, event: string, detail?: Record<string, unknown>) {
  const ts = new Date().toISOString();
  const parts = [`[${ts}] ${level} ${event}`];
  if (detail && Object.keys(detail).length > 0) {
    parts.push(JSON.stringify(detail));
  }
  if (level === "ERROR") {
    console.error(parts.join(" "));
  } else {
    console.log(parts.join(" "));
  }
}

export const logger = {
  info:  (event: string, detail?: Record<string, unknown>) => log("INFO",  event, detail),
  warn:  (event: string, detail?: Record<string, unknown>) => log("WARN",  event, detail),
  error: (event: string, detail?: Record<string, unknown>) => log("ERROR", event, detail),
};
