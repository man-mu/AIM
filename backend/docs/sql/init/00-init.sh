#!/bin/bash
# Postgres Docker init 脚本
# 此脚本在 PG 容器首次初始化时自动执行
# 职责：创建 nacos 库 + 导入 nacos schema 和 aim schema
# SQL 文件通过 docker-compose 挂载到 /aim-sql/（不放在 initdb 目录避免被 entrypoint 重复执行）
set -e

SQL_DIR="/aim-sql"

echo "=== AIM Postgres Init: 创建数据库 & 导入 schema ==="

# 1. 创建 nacos 数据库 (aim 由 POSTGRES_DB 自动创建)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE nacos;
EOSQL
echo "  ✓ nacos 数据库创建完成"

# 2. 导入 nacos schema
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname nacos -f "${SQL_DIR}/nacos-schema.sql"
echo "  ✓ nacos schema 导入完成"

# 3. 导入 aim schema
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "${SQL_DIR}/aim-schema.sql"
echo "  ✓ aim schema 导入完成"

echo "=== Init 完成: 13 张 nacos 表 + 6 个 aim schema ==="
