-- Nacos 配置中心初始化数据
-- 用法：Nacos 首次健康启动后执行
--   psql -h localhost -U postgres -d nacos -f docs/sql/init/nacos-init-data.sql
--   docker compose restart nacos
--
-- 幂等：已存在的行自动跳过

BEGIN;

-- ============================================================
-- 0. 管理员用户
--    Nacos v3 PG 模式不自动创建默认 admin；BCrypt hash 对应明文 nacos
--    如需修改密码：自备 bcrypt hash 替换下方字面量，或登入后通过控制台修改
-- ============================================================
INSERT INTO users (username, password, enabled)
SELECT 'nacos', '$2y$10$9GvPSQcSA8j4rvzPcoQY/O/MBQ477eIfUEogiaRr/E5eYp63iB6y.', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username='nacos');

INSERT INTO roles (username, role)
SELECT 'nacos', 'ROLE_ADMIN'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE username='nacos' AND role='ROLE_ADMIN');

-- ============================================================
-- 1. namespace: dev / test / prod
-- ============================================================
INSERT INTO tenant_info (kp, tenant_id, tenant_name, tenant_desc, create_source, gmt_create, gmt_modified)
SELECT '1', 'dev',  'dev',  '开发环境', 'nacos', (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
WHERE NOT EXISTS (SELECT 1 FROM tenant_info WHERE kp='1' AND tenant_id='dev');

INSERT INTO tenant_info (kp, tenant_id, tenant_name, tenant_desc, create_source, gmt_create, gmt_modified)
SELECT '1', 'test', 'test', '测试环境', 'nacos', (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
WHERE NOT EXISTS (SELECT 1 FROM tenant_info WHERE kp='1' AND tenant_id='test');

INSERT INTO tenant_info (kp, tenant_id, tenant_name, tenant_desc, create_source, gmt_create, gmt_modified)
SELECT '1', 'prod', 'prod', '生产环境', 'nacos', (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT, (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
WHERE NOT EXISTS (SELECT 1 FROM tenant_info WHERE kp='1' AND tenant_id='prod');

-- ============================================================
-- 2. 配置 DataId
-- ============================================================
DO $$
DECLARE
    shared_content TEXT := E'# 共享公共配置\nmybatis-plus:\n  configuration:\n    map-underscore-to-camel-case: true\n';
    user_svc_content TEXT := E'# user-service 主配置（敏感值由环境变量注入）\nspring:\n  datasource:\n    url: jdbc:postgresql://${DB_HOST:localhost}:5432/aim?currentSchema=%22user%22&stringtype=unspecified\n    username: ${DB_USER:postgres}\n    password: ${DB_PASSWORD:postgres}\n    driver-class-name: org.postgresql.Driver\n  data:\n    redis:\n      host: ${REDIS_HOST:localhost}\n      port: ${REDIS_PORT:6379}\ndubbo:\n  application:\n    name: user-service\n  protocol:\n    name: dubbo\n    port: 20881\n  registry:\n    address: nacos://${NACOS_USERNAME:nacos}:${NACOS_PASSWORD:nacos}@${NACOS_ADDR:localhost:8848}?namespace=${NACOS_NAMESPACE:dev}&group=${NACOS_GROUP:AIM_GROUP}\nmybatis-plus:\n  configuration:\n    map-underscore-to-camel-case: true\naim:\n  snowflake:\n    worker-id: 0\njwt:\n  secret: ${JWT_SECRET:aim-jwt-secret-key-change-in-production}\n  expire-sec: 7200\n  refresh-sec: 2592000\n';
    file_svc_content TEXT := E'# file-service 主配置（敏感值由环境变量注入）\nspring:\n  datasource:\n    url: jdbc:postgresql://${DB_HOST:localhost}:5432/aim?currentSchema=%22file%22&stringtype=unspecified\n    username: ${DB_USER:postgres}\n    password: ${DB_PASSWORD:postgres}\n    driver-class-name: org.postgresql.Driver\ndubbo:\n  application:\n    name: file-service\n  protocol:\n    name: dubbo\n    port: 20886\n  registry:\n    address: nacos://${NACOS_USERNAME:nacos}:${NACOS_PASSWORD:nacos}@${NACOS_ADDR:localhost:8848}?namespace=${NACOS_NAMESPACE:dev}&group=${NACOS_GROUP:AIM_GROUP}\naim:\n  snowflake:\n    worker-id: 4\nminio:\n  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}\n  access-key: ${MINIO_ACCESS_KEY:minioadmin}\n  secret-key: ${MINIO_SECRET_KEY:minioadmin}\n  bucket: ${MINIO_BUCKET:aim}\n';
    ns_list TEXT[] := ARRAY['dev', 'test', 'prod'];
    ns TEXT;
BEGIN
    FOREACH ns IN ARRAY ns_list LOOP
        INSERT INTO config_info (data_id, group_id, content, md5, src_ip, tenant_id, type)
        VALUES ('application.yml', 'COMMON_GROUP', shared_content, md5(shared_content), '127.0.0.1', ns, 'yaml')
        ON CONFLICT (data_id, group_id, tenant_id) DO NOTHING;

        INSERT INTO config_info (data_id, group_id, content, md5, src_ip, tenant_id, type)
        VALUES ('user-service.yml', 'AIM_GROUP', user_svc_content, md5(user_svc_content), '127.0.0.1', ns, 'yaml')
        ON CONFLICT (data_id, group_id, tenant_id) DO NOTHING;

        INSERT INTO config_info (data_id, group_id, content, md5, src_ip, tenant_id, type)
        VALUES ('file-service.yml', 'AIM_GROUP', file_svc_content, md5(file_svc_content), '127.0.0.1', ns, 'yaml')
        ON CONFLICT (data_id, group_id, tenant_id) DO NOTHING;
    END LOOP;
END $$;

COMMIT;
