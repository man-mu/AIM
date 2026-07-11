#!/bin/bash
# AIM 数据库初始化脚本 (幂等)
# 用途：非首次初始化时手动执行，创建 nacos/aim 库并导入 schema
# 用法: bash docs/sql/init-aim.sh
#
# 自动检测运行环境：
#   - 宿主机有 psql    → 直连 localhost:5432
#   - 宿主机无 psql    → 通过 docker exec aim-postgres psql 执行
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PG_USER="${POSTGRES_USER:-postgres}"
PG_CONTAINER="aim-postgres"

# --- 检测 psql 执行方式 ---
if command -v psql >/dev/null 2>&1; then
  PSQL="psql -h localhost -U ${PG_USER}"
  echo "检测到宿主机 psql，直连 localhost:5432"
else
  PSQL="docker exec -i ${PG_CONTAINER} psql -U ${PG_USER}"
  echo "未检测到宿主机 psql，通过 docker exec ${PG_CONTAINER} 执行"
fi

# --- 辅助函数 ---

# 执行单条 SQL 语句
exec_sql() {
  local db_name="$1"; shift
  ${PSQL} -d "${db_name}" -c "$@"
}

# 数据库存在则跳过
create_db_if_absent() {
  local db_name="$1"
  if ${PSQL} -d postgres -tc "SELECT 1 FROM pg_database WHERE datname = '${db_name}'" | grep -q 1; then
    echo "  → ${db_name} 库已存在，跳过创建"
  else
    ${PSQL} -d postgres -c "CREATE DATABASE ${db_name};"
    echo "  → ${db_name} 库创建成功"
  fi
}

# 导入 SQL 文件到指定库
# docker exec 模式下用 cat 管道传入（容器内无宿主机文件路径）
import_sql() {
  local db_name="$1"
  local sql_file="$2"
  if command -v psql >/dev/null 2>&1; then
    ${PSQL} -d "${db_name}" -f "${sql_file}"
  else
    cat "${sql_file}" | ${PSQL} -d "${db_name}"
  fi
}

echo "=== AIM 数据库初始化 ==="
echo ""

# 1. 创建 nacos 数据库
echo "[1/4] 创建 nacos 数据库..."
create_db_if_absent nacos

# 2. 创建 aim 数据库
echo "[2/4] 创建 aim 数据库..."
create_db_if_absent aim

# 3. 导入 nacos schema (CREATE TABLE IF NOT EXISTS，幂等)
echo "[3/4] 导入 nacos-schema.sql ..."
import_sql nacos "${SCRIPT_DIR}/nacos-schema.sql"
echo "  → nacos 表导入完成"

# 4. 导入 aim schema (CREATE SCHEMA/TABLE IF NOT EXISTS，幂等)
echo "[4/4] 导入 aim-schema.sql ..."
import_sql aim "${SCRIPT_DIR}/aim-schema.sql"
echo "  → aim schema 导入完成"

echo ""
echo "=== 初始化完成 ==="
echo "验证: ${PSQL} -d nacos -c '\dt'   # 应显示 13 张表"
echo "验证: ${PSQL} -d aim -c '\dn'     # 应显示 6 个 schema"
