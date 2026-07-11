#!/bin/bash
# AIM 数据库初始化脚本
# 创建 nacos 和 aim 两个数据库，并导入对应的 schema
# 用法: bash docs/sql/init-all.sh [pg_host] [pg_port]
# 默认: localhost:5432

set -e

PG_HOST="${1:-localhost}"
PG_PORT="${2:-5432}"
PG_USER="${POSTGRES_USER:-postgres}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== AIM 数据库初始化 ==="
echo "PG: ${PG_HOST}:${PG_PORT}"
echo "用户: ${PG_USER}"
echo ""

# 1. 创建 nacos 数据库
echo "[1/4] 创建 nacos 数据库..."
psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -tc \
  "SELECT 1 FROM pg_database WHERE datname = 'nacos'" | grep -q 1 \
  && echo "  → nacos 库已存在，跳过创建" \
  || { psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -c "CREATE DATABASE nacos;" \
       && echo "  → nacos 库创建成功"; }

# 2. 创建 aim 数据库
echo "[2/4] 创建 aim 数据库..."
psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -tc \
  "SELECT 1 FROM pg_database WHERE datname = 'aim'" | grep -q 1 \
  && echo "  → aim 库已存在，跳过创建" \
  || { psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -c "CREATE DATABASE aim;" \
       && echo "  → aim 库创建成功"; }

# 3. 导入 nacos schema
echo "[3/4] 导入 nacos-schema.sql ..."
psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d nacos -f "${SCRIPT_DIR}/nacos-schema.sql"
echo "  → nacos 表导入完成"

# 4. 导入 aim schema
echo "[4/4] 导入 aim-schema.sql ..."
psql -h "${PG_HOST}" -p "${PG_PORT}" -U "${PG_USER}" -d aim -f "${SCRIPT_DIR}/aim-schema.sql"
echo "  → aim 表导入完成"

echo ""
echo "=== 初始化完成 ==="
echo "验证: psql -h ${PG_HOST} -U ${PG_USER} -d nacos -c '\dt'   # 应显示 13 张表"
echo "验证: psql -h ${PG_HOST} -U ${PG_USER} -d aim -c '\dn'     # 应显示 6 个 schema"
