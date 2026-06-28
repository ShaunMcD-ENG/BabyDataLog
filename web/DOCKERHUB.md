# BabyDataLog – Sync Server

Self-hosted sync server for the BabyDataLog Android app. Lets multiple devices (e.g. both parents' phones) share feeding, nappy, growth, and milestone data in real time using last-write-wins conflict resolution.

## Features

- Web UI to approve/revoke paired devices
- Automatic last-write-wins sync with 2-day offline buffer
- Soft-delete propagation across devices
- Single SQLite database — no external dependencies

## Quick start

```bash
docker run -d \
  --name babydatalog \
  -p 3000:3000 \
  -v babydatalog-data:/data \
  -e SESSION_SECRET=your-random-secret-min-32-chars \
  dsmshaun/babydatalog:latest
```

Then open `http://<your-server>:3000` and follow the setup wizard to set an admin password.

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `SESSION_SECRET` | Yes | Random string (32+ chars) used to sign admin session cookies |
| `DATABASE_PATH` | No | Path to SQLite file (default: `/data/babydatalog.db`) |

## Volumes

Mount a volume at `/data` to persist the database across container restarts.

## Ports

Exposes port `3000`.
