#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="docker-compose.mysql.yml"

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker belum terpasang. Install Docker Desktop/Engine terlebih dahulu." >&2
  exit 1
fi

if docker compose version >/dev/null 2>&1; then
  COMPOSE_CMD=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE_CMD=(docker-compose)
else
  echo "docker compose atau docker-compose tidak ditemukan." >&2
  exit 1
fi

"${COMPOSE_CMD[@]}" -f "$COMPOSE_FILE" up -d

HOST_IP=$(hostname -I | awk '{print $1}')

cat <<EOF
MySQL aktif.

Host lokal  : 127.0.0.1
Host LAN    : ${HOST_IP:-<cek manual dengan ipconfig/ifconfig>}
Port        : 3306
Database    : daspos
User        : daspos
Password    : daspos123
Root pass   : root123

Tes koneksi:
  mysql -h 127.0.0.1 -P 3306 -u daspos -pdaspos123 -e "SHOW DATABASES;"
EOF
