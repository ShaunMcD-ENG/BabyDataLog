# BabyDataLog

Baby tracking app with Android companion and self-hosted web dashboard.

## Structure

```
BabyDataLog/
├── android/   # Kotlin + Jetpack Compose Android app
└── web/       # Next.js + TypeScript web dashboard (Docker)
```

## Web app quick start

```bash
cd web
cp .env.example .env.local

# Generate a password hash (run once):
node -e "const b=require('bcryptjs'); b.hash('yourpassword',12).then(console.log)"
# Paste the output into ADMIN_PASSWORD_HASH in .env.local

# Generate a session secret:
openssl rand -base64 32
# Paste into SESSION_SECRET in .env.local

npm install
npm run dev          # dev server on http://localhost:3000
npm run db:migrate   # create/update the SQLite schema
```

## Docker (Unraid)

```bash
cd web
docker compose up -d
```

The SQLite database is stored in a named Docker volume (`babydatalog-data`).
Map it to an Unraid share path in `docker-compose.yml` if you want direct file access.

## Sync

The Android app syncs to the web server via `POST /api/sync` (push) and
`GET /api/sync?lastSyncMs=...` (pull). Conflicts appear on the `/sync` page
for manual resolution.

To protect the sync endpoint, the Android app must send the session cookie
(obtained by logging in via `POST /api/auth/login` with the admin password).
