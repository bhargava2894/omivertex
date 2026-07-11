# Deploying OmiVertex

OmiVertex ships as a single self-contained jar: the React SPA (hashed assets) is
bundled inside, so there is one artifact and one process to run. Production is
driven entirely by the `prod` Spring profile plus environment variables — there
are **no secret fallbacks** in `application-prod.properties`, so the app will
refuse to start if a required variable is missing.

## Required environment variables (prod profile)

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL, e.g. `jdbc:postgresql://db-host:5432/omivertex` |
| `DB_USERNAME` | Database user |
| `DB_PASSWORD` | Database password |
| `OMIVERTEX_ADMIN_PASSWORD` | Password for the built-in `admin` account — **must not** be the dev default `Admin@123` (the app fails fast if it is) |
| `OMIVERTEX_VIEWER_PASSWORD` | Password for the built-in `viewer` account — must not be `Viewer@123` |

The `prod` profile also sets: `server.servlet.session.cookie.secure=true`
(needs TLS in front of the app), `spring.jpa.hibernate.ddl-auto=validate`,
`spring.flyway.enabled=true`, and `omivertex.seed=false`.

### Optional — real Google Sign-In

Server-side Google ID-token verification is wired but inert until a client ID is
configured (it fails closed otherwise, so no token is ever trusted):

| Variable | Purpose |
|---|---|
| `OMIVERTEX_AUTH_GOOGLE_CLIENT_ID` | OAuth 2.0 Web client ID; enables `GoogleApiTokenVerifier` |

The frontend needs the same client ID at build time as `VITE_GOOGLE_CLIENT_ID`
to render the Google Identity Services button and obtain the ID token the backend
verifies. Until both are set, only the built-in username/password login works.

### Optional — dashboard AI assistant (Gemini)

The "Ask OmiVertex AI" dashboard card is wired but inert until an API key is
configured (the endpoint fails closed with a clear 400 otherwise):

| Variable | Purpose |
|---|---|
| `OMIVERTEX_ASSISTANT_GEMINI_API_KEY` | Google AI Studio API key; enables `GeminiHttpClient` |
| `OMIVERTEX_ASSISTANT_GEMINI_MODEL` | Optional; defaults to `gemini-2.5-flash` |

Requires outbound HTTPS to `generativelanguage.googleapis.com` from the app
server. Each question sends the full live workforce summary as model context
(deliberate decision — see `docs/TODO.md`); resume file contents are never sent.

## Build

```bash
# 1. Frontend (produces frontend/dist with content-hashed assets + manifest)
cd frontend && npm ci && npm run build && cd ..

# 2. Backend jar (the maven build folds frontend/dist into the jar's static/)
./mvnw clean package        # runs the full test suite + Spotless/ArchUnit gates
# -> target/omivertex-0.0.1-SNAPSHOT.jar
```

## Run

Behind a TLS-terminating reverse proxy (nginx/Caddy/ALB) — the Secure cookie
requires HTTPS at the edge:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_URL=jdbc:postgresql://db-host:5432/omivertex
export DB_USERNAME=omivertex_app
export DB_PASSWORD='<strong-secret>'
export OMIVERTEX_ADMIN_PASSWORD='<strong-secret>'
export OMIVERTEX_VIEWER_PASSWORD='<strong-secret>'
java -jar target/omivertex-0.0.1-SNAPSHOT.jar
```

The reverse proxy must forward `X-Forwarded-Proto`/`-For` so Spring recognises
the request as HTTPS (set `server.forward-headers-strategy=framework` if fronted
by a proxy that isn't a servlet container).

## Container

A multi-stage `Dockerfile` at the repo root builds the SPA, builds the jar, and
produces a slim non-root JRE image with `SPRING_PROFILES_ACTIVE=prod` preset:

```bash
docker build -t omivertex:latest .
docker run -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://db-host:5432/omivertex \
  -e DB_USERNAME=omivertex_app \
  -e DB_PASSWORD='<strong-secret>' \
  -e OMIVERTEX_ADMIN_PASSWORD='<strong-secret>' \
  -e OMIVERTEX_VIEWER_PASSWORD='<strong-secret>' \
  omivertex:latest
```

## Schema migrations (Flyway)

The `prod` profile runs Flyway on startup and then validates the schema against
the JPA entities (`ddl-auto=validate`). `V1__baseline_schema.sql` under
`src/main/resources/db/migration` builds the whole schema on a **fresh** database.

- **First deploy:** point at an empty database; Flyway applies V1.
- **Every schema change after this:** add a new `V2__…`, `V3__…` migration.
  Never edit V1 and never rely on `ddl-auto` to alter a prod schema.
- Dev keeps `ddl-auto=update` and tests keep H2 `create-drop`, both with Flyway
  disabled, so Postgres migration SQL never runs against H2.

## Backups

`ops/backup.sh` takes a compressed, timestamped `pg_dump` and prunes old dumps.
Schedule it via cron once the team is entering real data:

```cron
15 2 * * * /opt/omivertex/ops/backup.sh >> /var/log/omivertex-backup.log 2>&1
```

Restore with:

```bash
gunzip -c omivertex-YYYYMMDD-HHMMSS.dump.gz | pg_restore -d omivertex --clean --if-exists
```
