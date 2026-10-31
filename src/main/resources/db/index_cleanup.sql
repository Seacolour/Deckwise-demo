-- ============================================================
-- index_cleanup.sql
-- 索引清理 & 补全脚本（手动执行）
-- 生成时间: 2026-02-10
-- ============================================================

BEGIN;

-- ============================================================
-- 1. 删除冗余索引（唯一约束已自带等价 B-tree 索引）
-- ============================================================

-- object_storage_files: uq_object_storage_files_object 已覆盖
DROP INDEX IF EXISTS idx_object_storage_files_object;

-- session_messages: uq_session_messages_seq(session_id, seq) 已覆盖
DROP INDEX IF EXISTS idx_session_messages_session_seq;

-- ============================================================
-- 2. 删除无用索引（代码中无对应查询模式）
-- ============================================================

-- session_messages: 所有查询按 seq 排序，无 created_at 排序/过滤
DROP INDEX IF EXISTS idx_session_messages_session_created_at;

-- sessions: 所有查询以 project_id 开头，无跨项目按 created_by 查询
DROP INDEX IF EXISTS idx_sessions_created_by_updated;

-- sessions: 缺少 updated_at，被 idx_sessions_project_updated 覆盖
DROP INDEX IF EXISTS idx_sessions_project_archived;

-- user_checkin_log: UNIQUE(user_id, checkin_date) 已覆盖，无按日期查所有用户的查询
DROP INDEX IF EXISTS idx_user_checkin_log_checkin_date;

-- ============================================================
-- 3. 删除废弃索引（session 维度文件授权已移除）
-- ============================================================

-- file_access_grants: session 维度授权已废弃，代码不再读写
DROP INDEX IF EXISTS uq_file_access_grants_session;
DROP INDEX IF EXISTS idx_file_access_grants_user_expires;

-- ============================================================
-- 4. 新增缺失索引
-- ============================================================

-- documents: findByProjectAndCurrentFileId 查询优化（替换单列索引）
DROP INDEX IF EXISTS idx_documents_current_file_id;
CREATE INDEX IF NOT EXISTS idx_documents_project_current_file
    ON documents (project_id, current_file_id)
    WHERE is_deleted = FALSE;

-- ============================================================
-- 5. 补全外键约束
-- ============================================================

-- user_rules.user_id → users(id)
ALTER TABLE user_rules
    ADD CONSTRAINT fk_user_rules_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- user_memories.user_id → users(id)
ALTER TABLE user_memories
    ADD CONSTRAINT fk_user_memories_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- documents.project_id → projects(id)
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE;

-- ============================================================
-- 6. 补全 updated_at 触发器
-- ============================================================

-- documents 表缺少自动更新 updated_at 的触发器
-- （当前代码手动 SET updated_at，但触发器可兜底防遗漏）
DROP TRIGGER IF EXISTS trg_documents_updated_at ON documents;
CREATE TRIGGER trg_documents_updated_at
    BEFORE UPDATE ON documents
    FOR EACH ROW EXECUTE FUNCTION backend_data.touch_updated_at();

COMMIT;
