#!/usr/bin/env bash
#
# OmiVertex PostgreSQL backup.
#
# Takes a compressed, timestamped pg_dump of the OmiVertex database and prunes
# dumps older than the retention window. The database is the business record
# once the team starts using it, so this is meant to run on a schedule (cron):
#
#   # daily at 02:15, keep 30 days, log to syslog
#   15 2 * * * /opt/omivertex/ops/backup.sh >> /var/log/omivertex-backup.log 2>&1
#
# Configure via environment (defaults suit a co-located DB):
#   PGHOST, PGPORT, PGUSER, PGDATABASE  — standard libpq vars
#   PGPASSWORD or ~/.pgpass             — credentials (never hard-code here)
#   BACKUP_DIR                          — where dumps land (default /var/backups/omivertex)
#   RETENTION_DAYS                      — how long to keep dumps (default 30)
set -euo pipefail

PGDATABASE="${PGDATABASE:-omivertex}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/omivertex}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"

timestamp="$(date +%Y%m%d-%H%M%S)"
outfile="${BACKUP_DIR}/omivertex-${timestamp}.dump.gz"

mkdir -p "${BACKUP_DIR}"

echo "[$(date -Iseconds)] backing up '${PGDATABASE}' -> ${outfile}"
# -Fc is the custom format (compressible, restorable with pg_restore); pipe through
# gzip for an extra pass. Fail the whole pipeline if pg_dump errors (pipefail).
pg_dump -Fc "${PGDATABASE}" | gzip > "${outfile}"

# Guard against a silent zero-byte dump masquerading as success.
if [ ! -s "${outfile}" ]; then
    echo "[$(date -Iseconds)] ERROR: backup file is empty, removing" >&2
    rm -f "${outfile}"
    exit 1
fi

echo "[$(date -Iseconds)] pruning dumps older than ${RETENTION_DAYS} days"
find "${BACKUP_DIR}" -name 'omivertex-*.dump.gz' -type f -mtime "+${RETENTION_DAYS}" -delete

echo "[$(date -Iseconds)] done ($(du -h "${outfile}" | cut -f1))"
# Restore with:  gunzip -c <file>.dump.gz | pg_restore -d omivertex --clean --if-exists
