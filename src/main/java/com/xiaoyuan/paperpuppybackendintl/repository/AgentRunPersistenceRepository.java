package com.xiaoyuan.paperpuppybackendintl.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jooq.impl.SQLDataType.BIGINT;
import static org.jooq.impl.SQLDataType.BOOLEAN;
import static org.jooq.impl.SQLDataType.CLOB;
import static org.jooq.impl.SQLDataType.INTEGER;
import static org.jooq.impl.SQLDataType.JSONB;
import static org.jooq.impl.SQLDataType.OFFSETDATETIME;

/**
 * Agent 阶段三 A 持久化仓储。
 *
 * <p>这里使用动态 DSL，避免第一版 schema 验证期频繁依赖 jOOQ codegen。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class AgentRunPersistenceRepository {
    private static final Pattern CN_PAGE_PATTERN = Pattern.compile("第\\s*(\\d{1,3})\\s*页");
    private static final Pattern EN_PAGE_PATTERN = Pattern.compile("(?i)\\bpage\\s*(\\d{1,3})\\b");
    private static final Pattern CN_PAGE_RANGE_PATTERN = Pattern.compile("第?\\s*(\\d{1,3})\\s*(?:-|~|到|至)\\s*(\\d{1,3})\\s*页");
    private static final int MAX_WRITE_IMPACT_PATHS = 24;

    private static final Table<?> AGENT_RUNS = DSL.table(DSL.name("agent_runs"));
    private static final Field<String> AR_RUN_ID = DSL.field(DSL.name("run_id"), CLOB.nullable(false));
    private static final Field<Long> AR_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> AR_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT);
    private static final Field<Long> AR_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> AR_GOAL = DSL.field(DSL.name("goal"), CLOB);
    private static final Field<String> AR_STATUS = DSL.field(DSL.name("status"), CLOB.nullable(false));
    private static final Field<String> AR_MODEL = DSL.field(DSL.name("model"), CLOB);
    private static final Field<OffsetDateTime> AR_STARTED_AT = DSL.field(DSL.name("started_at"), OFFSETDATETIME.nullable(false));
    private static final Field<OffsetDateTime> AR_ENDED_AT = DSL.field(DSL.name("ended_at"), OFFSETDATETIME);
    private static final Field<Integer> AR_TOTAL_MODEL_CALLS = DSL.field(DSL.name("total_model_calls"), INTEGER.nullable(false));
    private static final Field<Integer> AR_TOTAL_TOOL_CALLS = DSL.field(DSL.name("total_tool_calls"), INTEGER.nullable(false));
    private static final Field<Integer> AR_TOTAL_TOOL_RESULT_OPEN_CALLS = DSL.field(DSL.name("total_tool_result_open_calls"), INTEGER.nullable(false));
    private static final Field<Integer> AR_INPUT_TOKENS = DSL.field(DSL.name("input_tokens"), INTEGER);
    private static final Field<Integer> AR_OUTPUT_TOKENS = DSL.field(DSL.name("output_tokens"), INTEGER);
    private static final Field<Integer> AR_TOTAL_TOKENS = DSL.field(DSL.name("total_tokens"), INTEGER);
    private static final Field<String> AR_ERROR_MESSAGE = DSL.field(DSL.name("error_message"), CLOB);
    private static final Field<JSONB> AR_METADATA = DSL.field(DSL.name("metadata"), JSONB);

    private static final Table<?> AGENT_MODEL_CALLS = DSL.table(DSL.name("agent_model_calls"));
    private static final Field<String> AMC_RUN_ID = DSL.field(DSL.name("run_id"), CLOB.nullable(false));
    private static final Field<Long> AMC_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> AMC_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT);
    private static final Field<Long> AMC_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> AMC_PROVIDER = DSL.field(DSL.name("provider"), CLOB.nullable(false));
    private static final Field<String> AMC_CALL_SITE = DSL.field(DSL.name("call_site"), CLOB);
    private static final Field<String> AMC_MODEL = DSL.field(DSL.name("model"), CLOB);
    private static final Field<String> AMC_RESPONSE_ID = DSL.field(DSL.name("response_id"), CLOB);
    private static final Field<Integer> AMC_INPUT_ITEMS = DSL.field(DSL.name("input_items"), INTEGER);
    private static final Field<Integer> AMC_ESTIMATED_INPUT_CHARS = DSL.field(DSL.name("estimated_input_chars"), INTEGER);
    private static final Field<Integer> AMC_INSTRUCTIONS_CHARS = DSL.field(DSL.name("instructions_chars"), INTEGER);
    private static final Field<Integer> AMC_TOOLS_COUNT = DSL.field(DSL.name("tools_count"), INTEGER);
    private static final Field<Integer> AMC_MAX_OUTPUT_TOKENS = DSL.field(DSL.name("max_output_tokens"), INTEGER);
    private static final Field<String> AMC_SERVICE_TIER = DSL.field(DSL.name("service_tier"), CLOB);
    private static final Field<Integer> AMC_ATTEMPT = DSL.field(DSL.name("attempt"), INTEGER);
    private static final Field<Boolean> AMC_SUCCESS = DSL.field(DSL.name("success"), BOOLEAN);
    private static final Field<Integer> AMC_INPUT_TOKENS = DSL.field(DSL.name("input_tokens"), INTEGER);
    private static final Field<Integer> AMC_OUTPUT_TOKENS = DSL.field(DSL.name("output_tokens"), INTEGER);
    private static final Field<Integer> AMC_TOTAL_TOKENS = DSL.field(DSL.name("total_tokens"), INTEGER);
    private static final Field<Integer> AMC_ELAPSED_MS = DSL.field(DSL.name("elapsed_ms"), INTEGER);
    private static final Field<String> AMC_ERROR_TYPE = DSL.field(DSL.name("error_type"), CLOB);
    private static final Field<String> AMC_ERROR_MESSAGE = DSL.field(DSL.name("error_message"), CLOB);
    private static final Field<JSONB> AMC_RAW_USAGE = DSL.field(DSL.name("raw_usage"), JSONB);
    private static final Field<JSONB> AMC_METADATA = DSL.field(DSL.name("metadata"), JSONB);

    private static final Table<?> AGENT_CONTEXT_COMPACTIONS = DSL.table(DSL.name("agent_context_compactions"));
    private static final Field<Long> ACC_ID = DSL.field(DSL.name("id"), BIGINT.nullable(false));
    private static final Field<Long> ACC_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> ACC_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT.nullable(false));
    private static final Field<Long> ACC_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> ACC_SOURCE_AGENT_RUN_ID = DSL.field(DSL.name("source_agent_run_id"), CLOB);
    private static final Field<String> ACC_PROVIDER = DSL.field(DSL.name("provider"), CLOB.nullable(false));
    private static final Field<String> ACC_MODEL = DSL.field(DSL.name("model"), CLOB);
    private static final Field<String> ACC_MODE = DSL.field(DSL.name("mode"), CLOB.nullable(false));
    private static final Field<String> ACC_STATUS = DSL.field(DSL.name("status"), CLOB.nullable(false));
    private static final Field<String> ACC_CONTEXT_FORMAT = DSL.field(DSL.name("context_format"), CLOB.nullable(false));
    private static final Field<Integer> ACC_SOURCE_FIRST_SEQ = DSL.field(DSL.name("source_first_seq"), INTEGER);
    private static final Field<Integer> ACC_SOURCE_LAST_SEQ = DSL.field(DSL.name("source_last_seq"), INTEGER);
    private static final Field<Integer> ACC_SOURCE_FIRST_ITEM_IDX = DSL.field(DSL.name("source_first_item_idx"), INTEGER);
    private static final Field<Integer> ACC_SOURCE_LAST_ITEM_IDX = DSL.field(DSL.name("source_last_item_idx"), INTEGER);
    private static final Field<String> ACC_INSTRUCTIONS_HASH = DSL.field(DSL.name("instructions_hash"), CLOB);
    private static final Field<String> ACC_INPUT_FINGERPRINT = DSL.field(DSL.name("input_fingerprint"), CLOB);
    private static final Field<Integer> ACC_COMPACT_THRESHOLD = DSL.field(DSL.name("compact_threshold"), INTEGER);
    private static final Field<JSONB> ACC_COMPACTED_INPUT_JSON = DSL.field(DSL.name("compacted_input_json"), JSONB);
    private static final Field<Integer> ACC_COMPACTED_ITEM_COUNT = DSL.field(DSL.name("compacted_item_count"), INTEGER);
    private static final Field<Integer> ACC_ESTIMATED_INPUT_CHARS = DSL.field(DSL.name("estimated_input_chars"), INTEGER);
    private static final Field<Integer> ACC_ESTIMATED_SAVED_CHARS = DSL.field(DSL.name("estimated_saved_chars"), INTEGER);
    private static final Field<String> ACC_RESPONSE_ID = DSL.field(DSL.name("response_id"), CLOB);
    private static final Field<String> ACC_PREVIOUS_RESPONSE_ID = DSL.field(DSL.name("previous_response_id"), CLOB);
    private static final Field<JSONB> ACC_RAW_USAGE = DSL.field(DSL.name("raw_usage"), JSONB);
    private static final Field<JSONB> ACC_METADATA = DSL.field(DSL.name("metadata"), JSONB);
    private static final Field<String> ACC_ERROR_TYPE = DSL.field(DSL.name("error_type"), CLOB);
    private static final Field<String> ACC_ERROR_MESSAGE = DSL.field(DSL.name("error_message"), CLOB);
    private static final Field<OffsetDateTime> ACC_EXPIRES_AT = DSL.field(DSL.name("expires_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> ACC_SUPERSEDED_AT = DSL.field(DSL.name("superseded_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> ACC_CREATED_AT = DSL.field(DSL.name("created_at"), OFFSETDATETIME.nullable(false));

    private static final Table<?> AGENT_TOOL_CALLS = DSL.table(DSL.name("agent_tool_calls"));
    private static final Field<String> ATC_RUN_ID = DSL.field(DSL.name("run_id"), CLOB.nullable(false));
    private static final Field<String> ATC_CALL_ID = DSL.field(DSL.name("call_id"), CLOB.nullable(false));
    private static final Field<Long> ATC_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> ATC_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT);
    private static final Field<Long> ATC_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> ATC_TOOL_NAME = DSL.field(DSL.name("tool_name"), CLOB.nullable(false));
    private static final Field<String> ATC_ARGUMENTS_JSON = DSL.field(DSL.name("arguments_json"), CLOB);
    private static final Field<String> ATC_STATUS = DSL.field(DSL.name("status"), CLOB.nullable(false));
    private static final Field<Boolean> ATC_SUCCESS = DSL.field(DSL.name("success"), BOOLEAN);
    private static final Field<String> ATC_RESULT_SUMMARY = DSL.field(DSL.name("result_summary"), CLOB);
    private static final Field<String> ATC_ERROR_CODE = DSL.field(DSL.name("error_code"), CLOB);
    private static final Field<Integer> ATC_RESULT_CHARS = DSL.field(DSL.name("result_chars"), INTEGER);
    private static final Field<String> ATC_READ_KEY = DSL.field(DSL.name("read_key"), CLOB);
    private static final Field<String> ATC_DUPLICATE_OF_CALL_ID = DSL.field(DSL.name("duplicate_of_call_id"), CLOB);
    private static final Field<Integer> ATC_COST_MS = DSL.field(DSL.name("cost_ms"), INTEGER);
    private static final Field<OffsetDateTime> ATC_STARTED_AT = DSL.field(DSL.name("started_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> ATC_FINISHED_AT = DSL.field(DSL.name("finished_at"), OFFSETDATETIME);

    private static final Table<?> AGENT_TOOL_RESULTS = DSL.table(DSL.name("agent_tool_results"));
    private static final Field<String> ATR_RUN_ID = DSL.field(DSL.name("run_id"), CLOB.nullable(false));
    private static final Field<String> ATR_CALL_ID = DSL.field(DSL.name("call_id"), CLOB.nullable(false));
    private static final Field<String> ATR_TOOL_NAME = DSL.field(DSL.name("tool_name"), CLOB.nullable(false));
    private static final Field<JSONB> ATR_RESULT_JSON = DSL.field(DSL.name("result_json"), JSONB);
    private static final Field<Integer> ATR_RESULT_CHARS = DSL.field(DSL.name("result_chars"), INTEGER);
    private static final Field<Boolean> ATR_COMPACTED_FOR_MODEL = DSL.field(DSL.name("compacted_for_model"), BOOLEAN.nullable(false));
    private static final Field<OffsetDateTime> ATR_STORED_AT = DSL.field(DSL.name("stored_at"), OFFSETDATETIME.nullable(false));

    private static final Table<?> AGENT_RUN_LEDGER_SNAPSHOTS = DSL.table(DSL.name("agent_run_ledger_snapshots"));
    private static final Field<String> ARLS_RUN_ID = DSL.field(DSL.name("run_id"), CLOB.nullable(false));
    private static final Field<Long> ARLS_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> ARLS_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT);
    private static final Field<Long> ARLS_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<JSONB> ARLS_LEDGER_JSON = DSL.field(DSL.name("ledger_json"), JSONB.nullable(false));
    private static final Field<Integer> ARLS_MODEL_CALLS = DSL.field(DSL.name("model_calls"), INTEGER.nullable(false));
    private static final Field<Integer> ARLS_TOOL_CALLS = DSL.field(DSL.name("tool_calls"), INTEGER.nullable(false));
    private static final Field<Integer> ARLS_TOOL_RESULT_OPEN_CALLS = DSL.field(DSL.name("tool_result_open_calls"), INTEGER.nullable(false));
    private static final Field<OffsetDateTime> ARLS_UPDATED_AT = DSL.field(DSL.name("updated_at"), OFFSETDATETIME.nullable(false));

    private static final Table<?> AGENT_TASK_PROGRESS = DSL.table(DSL.name("agent_task_progress"));
    private static final Field<Long> ATP_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> ATP_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT.nullable(false));
    private static final Field<Long> ATP_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> ATP_PROJECT_SCOPE_KEY = DSL.field(DSL.name("project_scope_key"), CLOB.nullable(false));
    private static final Field<String> ATP_TASK_KEY = DSL.field(DSL.name("task_key"), CLOB.nullable(false));
    private static final Field<JSONB> ATP_PROJECTION_JSON = DSL.field(DSL.name("projection_json"), JSONB.nullable(false));
    private static final Field<String> ATP_SOURCE_RUN_ID = DSL.field(DSL.name("source_run_id"), CLOB);
    private static final Field<OffsetDateTime> ATP_SOURCE_UPDATED_AT = DSL.field(DSL.name("source_updated_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> ATP_EXPIRES_AT = DSL.field(DSL.name("expires_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> ATP_UPDATED_AT = DSL.field(DSL.name("updated_at"), OFFSETDATETIME.nullable(false));

    private static final Table<?> AGENT_ARTIFACT_PROJECTION = DSL.table(DSL.name("agent_artifact_projection"));
    private static final Field<Long> AAP_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> AAP_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT.nullable(false));
    private static final Field<Long> AAP_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> AAP_PROJECT_SCOPE_KEY = DSL.field(DSL.name("project_scope_key"), CLOB.nullable(false));
    private static final Field<String> AAP_ARTIFACT_KEY = DSL.field(DSL.name("artifact_key"), CLOB.nullable(false));
    private static final Field<Long> AAP_DOCUMENT_ID = DSL.field(DSL.name("document_id"), BIGINT);
    private static final Field<Long> AAP_FILE_ID = DSL.field(DSL.name("file_id"), BIGINT);
    private static final Field<String> AAP_LATEST_REVISION = DSL.field(DSL.name("latest_revision"), CLOB);
    private static final Field<JSONB> AAP_PROJECTION_JSON = DSL.field(DSL.name("projection_json"), JSONB.nullable(false));
    private static final Field<String> AAP_SOURCE_RUN_ID = DSL.field(DSL.name("source_run_id"), CLOB);
    private static final Field<OffsetDateTime> AAP_SOURCE_UPDATED_AT = DSL.field(DSL.name("source_updated_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> AAP_EXPIRES_AT = DSL.field(DSL.name("expires_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> AAP_UPDATED_AT = DSL.field(DSL.name("updated_at"), OFFSETDATETIME.nullable(false));

    private static final Table<?> AGENT_ARTIFACT_VIEWS = DSL.table(DSL.name("agent_artifact_views"));
    private static final Field<Long> AAV_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> AAV_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT.nullable(false));
    private static final Field<Long> AAV_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> AAV_PROJECT_SCOPE_KEY = DSL.field(DSL.name("project_scope_key"), CLOB.nullable(false));
    private static final Field<String> AAV_VIEW_KEY = DSL.field(DSL.name("view_key"), CLOB.nullable(false));
    private static final Field<Long> AAV_DOCUMENT_ID = DSL.field(DSL.name("document_id"), BIGINT);
    private static final Field<Long> AAV_FILE_ID = DSL.field(DSL.name("file_id"), BIGINT);
    private static final Field<String> AAV_TOOL_NAME = DSL.field(DSL.name("tool_name"), CLOB);
    private static final Field<String> AAV_VIEW_TYPE = DSL.field(DSL.name("view_type"), CLOB);
    private static final Field<String> AAV_READ_KEY = DSL.field(DSL.name("read_key"), CLOB);
    private static final Field<String> AAV_REVISION = DSL.field(DSL.name("revision"), CLOB);
    private static final Field<String> AAV_STATUS = DSL.field(DSL.name("status"), CLOB);
    private static final Field<Integer> AAV_RESULT_CHARS = DSL.field(DSL.name("result_chars"), INTEGER);
    private static final Field<JSONB> AAV_PROJECTION_JSON = DSL.field(DSL.name("projection_json"), JSONB.nullable(false));
    private static final Field<String> AAV_SOURCE_RUN_ID = DSL.field(DSL.name("source_run_id"), CLOB);
    private static final Field<String> AAV_SOURCE_CALL_ID = DSL.field(DSL.name("source_call_id"), CLOB);
    private static final Field<OffsetDateTime> AAV_SOURCE_UPDATED_AT = DSL.field(DSL.name("source_updated_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> AAV_EXPIRES_AT = DSL.field(DSL.name("expires_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> AAV_UPDATED_AT = DSL.field(DSL.name("updated_at"), OFFSETDATETIME.nullable(false));

    private static final Table<?> SESSION_AGENT_RUNS = DSL.table(DSL.name("session_agent_runs"));
    private static final Field<Long> SAR_ID = DSL.field(DSL.name("id"), BIGINT.nullable(false));
    private static final Field<Long> SAR_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> SAR_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT.nullable(false));
    private static final Field<Long> SAR_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> SAR_AGENT_RUN_ID = DSL.field(DSL.name("agent_run_id"), CLOB.nullable(false));
    private static final Field<Long> SAR_USER_MESSAGE_ID = DSL.field(DSL.name("user_message_id"), BIGINT);
    private static final Field<Long> SAR_ASSISTANT_MESSAGE_START_ID = DSL.field(DSL.name("assistant_message_start_id"), BIGINT);
    private static final Field<Long> SAR_ASSISTANT_MESSAGE_END_ID = DSL.field(DSL.name("assistant_message_end_id"), BIGINT);
    private static final Field<Integer> SAR_FIRST_MESSAGE_SEQ = DSL.field(DSL.name("first_message_seq"), INTEGER);
    private static final Field<Integer> SAR_LAST_MESSAGE_SEQ = DSL.field(DSL.name("last_message_seq"), INTEGER);
    private static final Field<String> SAR_STATUS = DSL.field(DSL.name("status"), CLOB.nullable(false));
    private static final Field<String> SAR_MAPPING_SOURCE = DSL.field(DSL.name("mapping_source"), CLOB.nullable(false));
    private static final Field<OffsetDateTime> SAR_STARTED_AT = DSL.field(DSL.name("started_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> SAR_ENDED_AT = DSL.field(DSL.name("ended_at"), OFFSETDATETIME);
    private static final Field<JSONB> SAR_METADATA = DSL.field(DSL.name("metadata"), JSONB.nullable(false));
    private static final Field<Boolean> SAR_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));

    private static final Table<?> SESSION_MESSAGES = DSL.table(DSL.name("session_messages"));
    private static final Field<Long> SM_ID = DSL.field(DSL.name("id"), BIGINT.nullable(false));
    private static final Field<Long> SM_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT.nullable(false));
    private static final Field<Integer> SM_SEQ = DSL.field(DSL.name("seq"), INTEGER.nullable(false));
    private static final Field<String> SM_ROLE = DSL.field(DSL.name("role"), CLOB.nullable(false));
    private static final Field<Boolean> SM_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));

    private static final Table<?> AGENT_RUN_DOCUMENT_REVISIONS = DSL.table(DSL.name("agent_run_document_revisions"));
    private static final Field<Long> ARDR_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> ARDR_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT.nullable(false));
    private static final Field<Long> ARDR_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> ARDR_AGENT_RUN_ID = DSL.field(DSL.name("agent_run_id"), CLOB.nullable(false));
    private static final Field<Long> ARDR_DOCUMENT_ID = DSL.field(DSL.name("document_id"), BIGINT.nullable(false));
    private static final Field<String> ARDR_DOCUMENT_TYPE = DSL.field(DSL.name("document_type"), CLOB);
    private static final Field<String> ARDR_CANONICAL_KIND = DSL.field(DSL.name("canonical_kind"), CLOB);
    private static final Field<String> ARDR_BEFORE_REVISION = DSL.field(DSL.name("before_revision"), CLOB);
    private static final Field<String> ARDR_AFTER_REVISION = DSL.field(DSL.name("after_revision"), CLOB);
    private static final Field<String> ARDR_BEFORE_REVISION_SOURCE = DSL.field(DSL.name("before_revision_source"), CLOB);
    private static final Field<String> ARDR_AFTER_REVISION_SOURCE = DSL.field(DSL.name("after_revision_source"), CLOB);
    private static final Field<String> ARDR_FIRST_WRITE_CALL_ID = DSL.field(DSL.name("first_write_call_id"), CLOB);
    private static final Field<String> ARDR_LAST_WRITE_CALL_ID = DSL.field(DSL.name("last_write_call_id"), CLOB);
    private static final Field<String> ARDR_FIRST_WRITE_TOOL_NAME = DSL.field(DSL.name("first_write_tool_name"), CLOB);
    private static final Field<String> ARDR_LAST_WRITE_TOOL_NAME = DSL.field(DSL.name("last_write_tool_name"), CLOB);
    private static final Field<Integer> ARDR_WRITE_COUNT = DSL.field(DSL.name("write_count"), INTEGER.nullable(false));
    private static final Field<JSONB> ARDR_MODIFIED_PATHS = DSL.field(DSL.name("modified_paths"), JSONB.nullable(false));
    private static final Field<JSONB> ARDR_TOUCHED_PAGES = DSL.field(DSL.name("touched_pages"), JSONB.nullable(false));
    private static final Field<String> ARDR_STATUS = DSL.field(DSL.name("status"), CLOB.nullable(false));
    private static final Field<Boolean> ARDR_ROLLBACK_VISIBLE = DSL.field(DSL.name("rollback_visible"), BOOLEAN.nullable(false));
    private static final Field<String> ARDR_ROLLBACK_DISABLED_REASON = DSL.field(DSL.name("rollback_disabled_reason"), CLOB);
    private static final Field<String> ARDR_SOURCE = DSL.field(DSL.name("source"), CLOB.nullable(false));
    private static final Field<OffsetDateTime> ARDR_FIRST_WRITE_AT = DSL.field(DSL.name("first_write_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> ARDR_LAST_WRITE_AT = DSL.field(DSL.name("last_write_at"), OFFSETDATETIME);
    private static final Field<OffsetDateTime> ARDR_COMPLETED_AT = DSL.field(DSL.name("completed_at"), OFFSETDATETIME);
    private static final Field<JSONB> ARDR_METADATA = DSL.field(DSL.name("metadata"), JSONB.nullable(false));
    private static final Field<Boolean> ARDR_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));

    private static final Table<?> DOCUMENT_REVISION_EVENTS = DSL.table(DSL.name("document_revision_events"));
    private static final Field<Long> DRE_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> DRE_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<Long> DRE_DOCUMENT_ID = DSL.field(DSL.name("document_id"), BIGINT.nullable(false));
    private static final Field<String> DRE_DOCUMENT_TYPE = DSL.field(DSL.name("document_type"), CLOB);
    private static final Field<String> DRE_CANONICAL_KIND = DSL.field(DSL.name("canonical_kind"), CLOB);
    private static final Field<Long> DRE_DOCUMENT_EVENT_SEQ = DSL.field(DSL.name("document_event_seq"), BIGINT.nullable(false));
    private static final Field<String> DRE_EVENT_TYPE = DSL.field(DSL.name("event_type"), CLOB.nullable(false));
    private static final Field<String> DRE_ACTOR_TYPE = DSL.field(DSL.name("actor_type"), CLOB.nullable(false));
    private static final Field<String> DRE_TRIGGER_SOURCE = DSL.field(DSL.name("trigger_source"), CLOB.nullable(false));
    private static final Field<String> DRE_STATUS = DSL.field(DSL.name("status"), CLOB.nullable(false));
    private static final Field<String> DRE_FROM_REVISION = DSL.field(DSL.name("from_revision"), CLOB);
    private static final Field<String> DRE_TO_REVISION = DSL.field(DSL.name("to_revision"), CLOB);
    private static final Field<Long> DRE_SOURCE_SESSION_ID = DSL.field(DSL.name("source_session_id"), BIGINT);
    private static final Field<String> DRE_SOURCE_AGENT_RUN_ID = DSL.field(DSL.name("source_agent_run_id"), CLOB);
    private static final Field<String> DRE_SOURCE_TOOL_CALL_ID = DSL.field(DSL.name("source_tool_call_id"), CLOB);
    private static final Field<String> DRE_SOURCE_TOOL_NAME = DSL.field(DSL.name("source_tool_name"), CLOB);
    private static final Field<JSONB> DRE_MODIFIED_PATHS = DSL.field(DSL.name("modified_paths"), JSONB.nullable(false));
    private static final Field<JSONB> DRE_TOUCHED_PAGES = DSL.field(DSL.name("touched_pages"), JSONB.nullable(false));
    private static final Field<String> DRE_SUMMARY = DSL.field(DSL.name("summary"), CLOB);
    private static final Field<JSONB> DRE_METADATA = DSL.field(DSL.name("metadata"), JSONB.nullable(false));
    private static final Field<Boolean> DRE_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));

    private final DSLContext dsl;
    @Qualifier("jackson2Mapper")
    private final ObjectMapper objectMapper;

    public void upsertRun(RunEntry entry) {
        dsl.insertInto(AGENT_RUNS)
                .set(AR_RUN_ID, entry.getRunId())
                .set(AR_USER_ID, entry.getUserId())
                .set(AR_SESSION_ID, nullablePositive(entry.getSessionId()))
                .set(AR_PROJECT_ID, nullablePositive(entry.getProjectId()))
                .set(AR_GOAL, blankToNull(entry.getGoal()))
                .set(AR_STATUS, defaultText(entry.getStatus(), "running"))
                .set(AR_MODEL, blankToNull(entry.getModel()))
                .set(AR_STARTED_AT, entry.getStartedAt() == null ? OffsetDateTime.now() : entry.getStartedAt())
                .set(AR_METADATA, toJsonbOrNull(entry.getMetadataJson()))
                .onConflict(AR_RUN_ID)
                .doUpdate()
                .set(AR_USER_ID, entry.getUserId())
                .set(AR_SESSION_ID, nullablePositive(entry.getSessionId()))
                .set(AR_PROJECT_ID, nullablePositive(entry.getProjectId()))
                .set(AR_GOAL, blankToNull(entry.getGoal()))
                .set(AR_STATUS, defaultText(entry.getStatus(), "running"))
                .set(AR_MODEL, blankToNull(entry.getModel()))
                .set(AR_METADATA, toJsonbOrNull(entry.getMetadataJson()))
                .execute();
        try {
            upsertSessionAgentRunFromRun(entry);
        } catch (Exception e) {
            log.warn("Sync session_agent_runs on run upsert failed: runId={}, error={}",
                    entry.getRunId(), e.getMessage());
        }
    }

    public void finishRun(FinishRunEntry entry) {
        OffsetDateTime endedAt = entry.getEndedAt() == null ? OffsetDateTime.now() : entry.getEndedAt();
        dsl.update(AGENT_RUNS)
                .set(AR_STATUS, defaultText(entry.getStatus(), "completed"))
                .set(AR_ENDED_AT, endedAt)
                .set(AR_TOTAL_MODEL_CALLS, nonNegative(entry.getTotalModelCalls()))
                .set(AR_TOTAL_TOOL_CALLS, nonNegative(entry.getTotalToolCalls()))
                .set(AR_TOTAL_TOOL_RESULT_OPEN_CALLS, nonNegative(entry.getTotalToolResultOpenCalls()))
                .set(AR_INPUT_TOKENS, nullableNonNegative(entry.getInputTokens()))
                .set(AR_OUTPUT_TOKENS, nullableNonNegative(entry.getOutputTokens()))
                .set(AR_TOTAL_TOKENS, nullableNonNegative(entry.getTotalTokens()))
                .set(AR_ERROR_MESSAGE, blankToNull(entry.getErrorMessage()))
                .set(AR_METADATA, toJsonbOrNull(entry.getMetadataJson()))
                .where(AR_RUN_ID.eq(entry.getRunId()))
                .execute();
        try {
            finishSessionAgentRun(entry, endedAt);
        } catch (Exception e) {
            log.warn("Sync session_agent_runs on run finish failed: runId={}, error={}",
                    entry.getRunId(), e.getMessage());
        }
        try {
            finishDocumentRevisionIndexes(entry.getRunId(), endedAt);
        } catch (Exception e) {
            log.warn("Finalize agent_run_document_revisions failed: runId={}, error={}",
                    entry.getRunId(), e.getMessage());
        }
    }

    public int markLatestRunningRunCancelled(long userId, long sessionId, String reason) {
        if (userId <= 0 || sessionId <= 0) {
            return 0;
        }
        Condition condition = AR_USER_ID.eq(userId)
                .and(AR_SESSION_ID.eq(sessionId))
                .and(AR_STATUS.eq("running"))
                .and(AR_ENDED_AT.isNull());
        String latestRunId = dsl.select(AR_RUN_ID)
                .from(AGENT_RUNS)
                .where(condition)
                .orderBy(AR_STARTED_AT.desc())
                .limit(1)
                .fetchOne(AR_RUN_ID);
        if (latestRunId == null || latestRunId.isBlank()) {
            return 0;
        }
        OffsetDateTime cancelledAt = OffsetDateTime.now();
        int updated = dsl.update(AGENT_RUNS)
                .set(AR_STATUS, "cancelled")
                .set(AR_ENDED_AT, cancelledAt)
                .set(AR_ERROR_MESSAGE, abbreviate(defaultText(reason, "backend_cancelled"), 1_000))
                .where(AR_RUN_ID.eq(latestRunId))
                .execute();
        if (updated > 0) {
            try {
                markSessionAgentRunCancelled(latestRunId, cancelledAt);
            } catch (Exception e) {
                log.warn("Sync session_agent_runs on run cancel failed: runId={}, error={}",
                        latestRunId, e.getMessage());
            }
            try {
                finishDocumentRevisionIndexes(latestRunId, cancelledAt);
            } catch (Exception e) {
                log.warn("Finalize agent_run_document_revisions on run cancel failed: runId={}, error={}",
                        latestRunId, e.getMessage());
            }
        }
        return updated;
    }

    public void bindLatestSessionAgentRunMessageSpan(long userId,
                                                     long sessionId,
                                                     Long assistantMessageStartId,
                                                     Long assistantMessageEndId,
                                                     int firstMessageSeq,
                                                     int lastMessageSeq) {
        if (userId <= 0 || sessionId <= 0 || firstMessageSeq <= 0 || lastMessageSeq < firstMessageSeq) {
            return;
        }
        var latest = dsl.select(SAR_ID, SAR_AGENT_RUN_ID)
                .from(SESSION_AGENT_RUNS)
                .where(SAR_USER_ID.eq(userId)
                        .and(SAR_SESSION_ID.eq(sessionId))
                        .and(SAR_IS_DELETED.eq(false)))
                .orderBy(SAR_STARTED_AT.desc().nullsLast(), SAR_ID.desc())
                .limit(1)
                .fetchOne();
        if (latest == null || latest.get(SAR_ID) == null) {
            return;
        }

        String latestRunId = latest.get(SAR_AGENT_RUN_ID);
        Long userMessageId = findLatestUserMessageIdBeforeSeq(sessionId, firstMessageSeq);
        dsl.update(SESSION_AGENT_RUNS)
                .set(SAR_USER_MESSAGE_ID, userMessageId)
                .set(SAR_ASSISTANT_MESSAGE_START_ID, nullablePositive(assistantMessageStartId))
                .set(SAR_ASSISTANT_MESSAGE_END_ID, nullablePositive(assistantMessageEndId))
                .set(SAR_FIRST_MESSAGE_SEQ, firstMessageSeq)
                .set(SAR_LAST_MESSAGE_SEQ, lastMessageSeq)
                .set(SAR_MAPPING_SOURCE, "backend_stream")
                .where(SAR_ID.eq(latest.get(SAR_ID)))
                .execute();
        bindContextCompactionSourceSeq(latestRunId, lastMessageSeq);
    }

    private void bindContextCompactionSourceSeq(String agentRunId, int sourceLastSeq) {
        String runId = blankToNull(agentRunId);
        if (runId == null || sourceLastSeq <= 0) {
            return;
        }
        dsl.update(AGENT_CONTEXT_COMPACTIONS)
                .set(ACC_SOURCE_LAST_SEQ, sourceLastSeq)
                .where(ACC_SOURCE_AGENT_RUN_ID.eq(runId))
                .and(ACC_STATUS.eq("active"))
                .and(ACC_SOURCE_LAST_SEQ.isNull().or(ACC_SOURCE_LAST_SEQ.lt(sourceLastSeq)))
                .execute();
    }

    public List<SessionAgentRunMessageSpan> listSessionAgentRunMessageSpans(long sessionId,
                                                                            Integer firstSeq,
                                                                            Integer lastSeq) {
        if (sessionId <= 0) {
            return List.of();
        }
        Condition condition = SAR_SESSION_ID.eq(sessionId)
                .and(SAR_IS_DELETED.eq(false))
                .and(SAR_FIRST_MESSAGE_SEQ.isNotNull())
                .and(SAR_LAST_MESSAGE_SEQ.isNotNull());
        if (firstSeq != null && lastSeq != null && firstSeq > 0 && lastSeq >= firstSeq) {
            condition = condition
                    .and(SAR_FIRST_MESSAGE_SEQ.le(lastSeq))
                    .and(SAR_LAST_MESSAGE_SEQ.ge(firstSeq));
        }
        return dsl.select(SAR_ID, SAR_AGENT_RUN_ID, SAR_STATUS, SAR_FIRST_MESSAGE_SEQ, SAR_LAST_MESSAGE_SEQ)
                .from(SESSION_AGENT_RUNS)
                .where(condition)
                .orderBy(SAR_FIRST_MESSAGE_SEQ.asc(), SAR_ID.asc())
                .fetch(record -> new SessionAgentRunMessageSpan(
                        record.get(SAR_ID),
                        record.get(SAR_AGENT_RUN_ID),
                        record.get(SAR_STATUS),
                        record.get(SAR_FIRST_MESSAGE_SEQ),
                        record.get(SAR_LAST_MESSAGE_SEQ)
                ));
    }

    public void recordModelCall(ModelCallEntry entry) {
        dsl.insertInto(AGENT_MODEL_CALLS)
                .set(AMC_RUN_ID, entry.getRunId())
                .set(AMC_USER_ID, entry.getUserId())
                .set(AMC_SESSION_ID, nullablePositive(entry.getSessionId()))
                .set(AMC_PROJECT_ID, nullablePositive(entry.getProjectId()))
                .set(AMC_PROVIDER, defaultText(entry.getProvider(), "openai"))
                .set(AMC_CALL_SITE, blankToNull(entry.getCallSite()))
                .set(AMC_MODEL, blankToNull(entry.getModel()))
                .set(AMC_RESPONSE_ID, blankToNull(entry.getResponseId()))
                .set(AMC_INPUT_ITEMS, nullableNonNegative(entry.getInputItems()))
                .set(AMC_ESTIMATED_INPUT_CHARS, nullableNonNegative(entry.getEstimatedInputChars()))
                .set(AMC_INSTRUCTIONS_CHARS, nullableNonNegative(entry.getInstructionsChars()))
                .set(AMC_TOOLS_COUNT, nullableNonNegative(entry.getToolsCount()))
                .set(AMC_MAX_OUTPUT_TOKENS, nullableNonNegative(entry.getMaxOutputTokens()))
                .set(AMC_SERVICE_TIER, blankToNull(entry.getServiceTier()))
                .set(AMC_ATTEMPT, nullableNonNegative(entry.getAttempt()))
                .set(AMC_SUCCESS, entry.getSuccess())
                .set(AMC_INPUT_TOKENS, nullableNonNegative(entry.getInputTokens()))
                .set(AMC_OUTPUT_TOKENS, nullableNonNegative(entry.getOutputTokens()))
                .set(AMC_TOTAL_TOKENS, nullableNonNegative(entry.getTotalTokens()))
                .set(AMC_ELAPSED_MS, nullableNonNegative(entry.getElapsedMs()))
                .set(AMC_ERROR_TYPE, blankToNull(entry.getErrorType()))
                .set(AMC_ERROR_MESSAGE, blankToNull(entry.getErrorMessage()))
                .set(AMC_RAW_USAGE, toJsonbOrNull(entry.getRawUsageJson()))
                .set(AMC_METADATA, toJsonbOrNull(entry.getMetadataJson()))
                .execute();
    }

    public void recordContextCompaction(ContextCompactionEntry entry) {
        String provider = normalizeContextCompactionProvider(entry.getProvider());
        String status = normalizeContextCompactionStatus(entry.getStatus());
        JSONB compactedInputJson = toJsonbOrNull(entry.getCompactedInputJson());
        if (!"failed".equals(status) && compactedInputJson == null) {
            throw new IllegalArgumentException("compacted_input_json is required for active context compaction");
        }
        JSONB rawUsage = jsonbObjectOrNull(entry.getRawUsageJson());
        JSONB metadata = jsonbObjectOrEmpty(entry.getMetadataJson());
        dsl.transaction(configuration -> {
            DSLContext tx = DSL.using(configuration);
            if ("active".equals(status)) {
                tx.update(AGENT_CONTEXT_COMPACTIONS)
                        .set(ACC_STATUS, "superseded")
                        .set(ACC_SUPERSEDED_AT, OffsetDateTime.now())
                        .where(ACC_SESSION_ID.eq(entry.getSessionId()))
                        .and(ACC_PROVIDER.eq(provider))
                        .and(ACC_STATUS.eq("active"))
                        .execute();
            }
            tx.insertInto(AGENT_CONTEXT_COMPACTIONS)
                    .set(ACC_USER_ID, entry.getUserId())
                    .set(ACC_SESSION_ID, entry.getSessionId())
                    .set(ACC_PROJECT_ID, nullablePositive(entry.getProjectId()))
                    .set(ACC_SOURCE_AGENT_RUN_ID, blankToNull(entry.getRunId()))
                    .set(ACC_PROVIDER, provider)
                    .set(ACC_MODEL, blankToNull(entry.getModel()))
                    .set(ACC_MODE, normalizeContextCompactionMode(entry.getMode()))
                    .set(ACC_STATUS, status)
                    .set(ACC_CONTEXT_FORMAT, defaultText(entry.getContextFormat(), "responses_compacted_window_v1"))
                    .set(ACC_SOURCE_FIRST_SEQ, nullablePositive(entry.getSourceFirstSeq()))
                    .set(ACC_SOURCE_LAST_SEQ, nullablePositive(entry.getSourceLastSeq()))
                    .set(ACC_SOURCE_FIRST_ITEM_IDX, nullableNonNegative(entry.getSourceFirstItemIdx()))
                    .set(ACC_SOURCE_LAST_ITEM_IDX, nullableNonNegative(entry.getSourceLastItemIdx()))
                    .set(ACC_INSTRUCTIONS_HASH, blankToNull(entry.getInstructionsHash()))
                    .set(ACC_INPUT_FINGERPRINT, blankToNull(entry.getInputFingerprint()))
                    .set(ACC_COMPACT_THRESHOLD, nullablePositive(entry.getCompactThreshold()))
                    .set(ACC_COMPACTED_INPUT_JSON, compactedInputJson)
                    .set(ACC_COMPACTED_ITEM_COUNT, nullableNonNegative(entry.getCompactedItemCount()))
                    .set(ACC_ESTIMATED_INPUT_CHARS, nullableNonNegative(entry.getEstimatedInputChars()))
                    .set(ACC_ESTIMATED_SAVED_CHARS, nullableNonNegative(entry.getEstimatedSavedChars()))
                    .set(ACC_RESPONSE_ID, blankToNull(entry.getResponseId()))
                    .set(ACC_PREVIOUS_RESPONSE_ID, blankToNull(entry.getPreviousResponseId()))
                    .set(ACC_RAW_USAGE, rawUsage)
                    .set(ACC_METADATA, metadata)
                    .set(ACC_ERROR_TYPE, blankToNull(entry.getErrorType()))
                    .set(ACC_ERROR_MESSAGE, blankToNull(entry.getErrorMessage()))
                    .execute();
        });
    }

    public Optional<ActiveContextCompaction> findActiveContextCompaction(long userId,
                                                                         long sessionId,
                                                                         String provider) {
        if (userId <= 0 || sessionId <= 0) {
            return Optional.empty();
        }
        String normalizedProvider = normalizeContextCompactionProvider(provider);
        OffsetDateTime now = OffsetDateTime.now();
        var record = dsl.select(
                        ACC_ID,
                        ACC_PROVIDER,
                        ACC_CONTEXT_FORMAT,
                        ACC_COMPACTED_INPUT_JSON,
                        ACC_SOURCE_LAST_SEQ,
                        ACC_MODEL,
                        ACC_RESPONSE_ID,
                        ACC_CREATED_AT)
                .from(AGENT_CONTEXT_COMPACTIONS)
                .where(ACC_USER_ID.eq(userId))
                .and(ACC_SESSION_ID.eq(sessionId))
                .and(ACC_PROVIDER.eq(normalizedProvider))
                .and(ACC_STATUS.eq("active"))
                .and(ACC_COMPACTED_INPUT_JSON.isNotNull())
                .and(ACC_EXPIRES_AT.isNull().or(ACC_EXPIRES_AT.gt(now)))
                .orderBy(ACC_CREATED_AT.desc(), ACC_ID.desc())
                .limit(1)
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        JSONB compactedJson = record.get(ACC_COMPACTED_INPUT_JSON);
        if (compactedJson == null || compactedJson.data() == null || compactedJson.data().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ActiveContextCompaction(
                record.get(ACC_ID),
                record.get(ACC_PROVIDER),
                record.get(ACC_CONTEXT_FORMAT),
                compactedJson.data(),
                record.get(ACC_SOURCE_LAST_SEQ),
                record.get(ACC_MODEL),
                record.get(ACC_RESPONSE_ID),
                record.get(ACC_CREATED_AT)
        ));
    }

    public void recordToolCall(ToolCallEntry entry) {
        dsl.insertInto(AGENT_TOOL_CALLS)
                .set(ATC_RUN_ID, entry.getRunId())
                .set(ATC_CALL_ID, entry.getCallId())
                .set(ATC_USER_ID, entry.getUserId())
                .set(ATC_SESSION_ID, nullablePositive(entry.getSessionId()))
                .set(ATC_PROJECT_ID, nullablePositive(entry.getProjectId()))
                .set(ATC_TOOL_NAME, defaultText(entry.getToolName(), "unknown"))
                .set(ATC_ARGUMENTS_JSON, blankToNull(entry.getArgumentsJson()))
                .set(ATC_STATUS, defaultText(entry.getStatus(), "unknown"))
                .set(ATC_SUCCESS, entry.getSuccess())
                .set(ATC_RESULT_SUMMARY, blankToNull(entry.getResultSummary()))
                .set(ATC_ERROR_CODE, blankToNull(entry.getErrorCode()))
                .set(ATC_RESULT_CHARS, nullableNonNegative(entry.getResultChars()))
                .set(ATC_READ_KEY, blankToNull(entry.getReadKey()))
                .set(ATC_DUPLICATE_OF_CALL_ID, blankToNull(entry.getDuplicateOfCallId()))
                .set(ATC_COST_MS, nullableNonNegative(entry.getCostMs()))
                .set(ATC_STARTED_AT, entry.getStartedAt())
                .set(ATC_FINISHED_AT, entry.getFinishedAt())
                .onConflict(ATC_RUN_ID, ATC_CALL_ID)
                .doUpdate()
                .set(ATC_TOOL_NAME, defaultText(entry.getToolName(), "unknown"))
                .set(ATC_ARGUMENTS_JSON, blankToNull(entry.getArgumentsJson()))
                .set(ATC_STATUS, defaultText(entry.getStatus(), "unknown"))
                .set(ATC_SUCCESS, entry.getSuccess())
                .set(ATC_RESULT_SUMMARY, blankToNull(entry.getResultSummary()))
                .set(ATC_ERROR_CODE, blankToNull(entry.getErrorCode()))
                .set(ATC_RESULT_CHARS, nullableNonNegative(entry.getResultChars()))
                .set(ATC_READ_KEY, blankToNull(entry.getReadKey()))
                .set(ATC_DUPLICATE_OF_CALL_ID, blankToNull(entry.getDuplicateOfCallId()))
                .set(ATC_COST_MS, nullableNonNegative(entry.getCostMs()))
                .set(ATC_STARTED_AT, entry.getStartedAt())
                .set(ATC_FINISHED_AT, entry.getFinishedAt())
                .execute();

        JSONB resultJson = toJsonbOrNull(entry.getResultJson());
        if (resultJson != null) {
            dsl.insertInto(AGENT_TOOL_RESULTS)
                    .set(ATR_RUN_ID, entry.getRunId())
                    .set(ATR_CALL_ID, entry.getCallId())
                    .set(ATR_TOOL_NAME, defaultText(entry.getToolName(), "unknown"))
                    .set(ATR_RESULT_JSON, resultJson)
                    .set(ATR_RESULT_CHARS, nullableNonNegative(entry.getResultChars()))
                    .set(ATR_COMPACTED_FOR_MODEL, Boolean.TRUE.equals(entry.getCompactedForModel()))
                    .set(ATR_STORED_AT, entry.getFinishedAt() == null ? OffsetDateTime.now() : entry.getFinishedAt())
                    .onConflict(ATR_RUN_ID, ATR_CALL_ID)
                    .doUpdate()
                    .set(ATR_TOOL_NAME, defaultText(entry.getToolName(), "unknown"))
                    .set(ATR_RESULT_JSON, resultJson)
                    .set(ATR_RESULT_CHARS, nullableNonNegative(entry.getResultChars()))
                    .set(ATR_COMPACTED_FOR_MODEL, Boolean.TRUE.equals(entry.getCompactedForModel()))
                    .set(ATR_STORED_AT, entry.getFinishedAt() == null ? OffsetDateTime.now() : entry.getFinishedAt())
                    .execute();
        }
        applyArtifactWriteImpact(entry);
    }

    public void upsertLedgerSnapshot(LedgerSnapshotEntry entry) {
        JSONB ledgerJson = toJsonbOrNull(entry.getLedgerJson());
        if (ledgerJson == null) {
            log.warn("Skip invalid Agent ledger snapshot JSON: runId={}", entry.getRunId());
            return;
        }
        dsl.insertInto(AGENT_RUN_LEDGER_SNAPSHOTS)
                .set(ARLS_RUN_ID, entry.getRunId())
                .set(ARLS_USER_ID, entry.getUserId())
                .set(ARLS_SESSION_ID, nullablePositive(entry.getSessionId()))
                .set(ARLS_PROJECT_ID, nullablePositive(entry.getProjectId()))
                .set(ARLS_LEDGER_JSON, ledgerJson)
                .set(ARLS_MODEL_CALLS, nonNegative(entry.getModelCalls()))
                .set(ARLS_TOOL_CALLS, nonNegative(entry.getToolCalls()))
                .set(ARLS_TOOL_RESULT_OPEN_CALLS, nonNegative(entry.getToolResultOpenCalls()))
                .onConflict(ARLS_RUN_ID)
                .doUpdate()
                .set(ARLS_USER_ID, entry.getUserId())
                .set(ARLS_SESSION_ID, nullablePositive(entry.getSessionId()))
                .set(ARLS_PROJECT_ID, nullablePositive(entry.getProjectId()))
                .set(ARLS_LEDGER_JSON, ledgerJson)
                .set(ARLS_MODEL_CALLS, nonNegative(entry.getModelCalls()))
                .set(ARLS_TOOL_CALLS, nonNegative(entry.getToolCalls()))
                .set(ARLS_TOOL_RESULT_OPEN_CALLS, nonNegative(entry.getToolResultOpenCalls()))
                .execute();
        upsertProjectionState(entry, ledgerJson.data());
    }

    private void upsertProjectionState(LedgerSnapshotEntry entry, String rawLedgerJson) {
        Long sessionId = nullablePositive(entry.getSessionId());
        if (entry.getUserId() == null || entry.getUserId() <= 0 || sessionId == null) {
            return;
        }
        try {
            OffsetDateTime projectedAt = OffsetDateTime.now();
            JsonNode snapshot = objectMapper.readTree(Objects.toString(rawLedgerJson, ""));
            Long projectId = nullablePositive(entry.getProjectId());
            String scopeKey = projectionScopeKey(projectId);

            ObjectNode taskProjection = buildTaskProjection(snapshot, projectedAt, false);
            dsl.insertInto(AGENT_TASK_PROGRESS)
                    .set(ATP_USER_ID, entry.getUserId())
                    .set(ATP_SESSION_ID, sessionId)
                    .set(ATP_PROJECT_ID, projectId)
                    .set(ATP_PROJECT_SCOPE_KEY, scopeKey)
                    .set(ATP_TASK_KEY, "default")
                    .set(ATP_PROJECTION_JSON, toJsonbOrNull(taskProjection))
                    .set(ATP_SOURCE_RUN_ID, entry.getRunId())
                    .set(ATP_SOURCE_UPDATED_AT, projectedAt)
                    .set(ATP_EXPIRES_AT, (OffsetDateTime) null)
                    .onConflict(ATP_USER_ID, ATP_SESSION_ID, ATP_PROJECT_SCOPE_KEY, ATP_TASK_KEY)
                    .doUpdate()
                    .set(ATP_PROJECT_ID, projectId)
                    .set(ATP_PROJECTION_JSON, toJsonbOrNull(taskProjection))
                    .set(ATP_SOURCE_RUN_ID, entry.getRunId())
                    .set(ATP_SOURCE_UPDATED_AT, projectedAt)
                    .set(ATP_EXPIRES_AT, (OffsetDateTime) null)
                    .execute();

            ObjectNode artifactProjection = buildArtifactProjection(snapshot, projectedAt, false);
            mergePathAwareStatusesFromStoredViews(entry.getUserId(), sessionId, scopeKey, artifactProjection);
            ObjectNode activeDocument = activeDocument(snapshot);
            Long documentId = positiveLong(activeDocument.path("documentId"));
            Long fileId = positiveLong(activeDocument.path("fileId"));
            String latestRevision = firstNonBlank(text(activeDocument.path("revision")),
                    text(activeDocument.path("latestDocumentRevision")));
            dsl.insertInto(AGENT_ARTIFACT_PROJECTION)
                    .set(AAP_USER_ID, entry.getUserId())
                    .set(AAP_SESSION_ID, sessionId)
                    .set(AAP_PROJECT_ID, projectId)
                    .set(AAP_PROJECT_SCOPE_KEY, scopeKey)
                    .set(AAP_ARTIFACT_KEY, "session_artifacts")
                    .set(AAP_DOCUMENT_ID, documentId)
                    .set(AAP_FILE_ID, fileId)
                    .set(AAP_LATEST_REVISION, blankToNull(latestRevision))
                    .set(AAP_PROJECTION_JSON, toJsonbOrNull(artifactProjection))
                    .set(AAP_SOURCE_RUN_ID, entry.getRunId())
                    .set(AAP_SOURCE_UPDATED_AT, projectedAt)
                    .set(AAP_EXPIRES_AT, (OffsetDateTime) null)
                    .onConflict(AAP_USER_ID, AAP_SESSION_ID, AAP_PROJECT_SCOPE_KEY, AAP_ARTIFACT_KEY)
                    .doUpdate()
                    .set(AAP_PROJECT_ID, projectId)
                    .set(AAP_DOCUMENT_ID, documentId)
                    .set(AAP_FILE_ID, fileId)
                    .set(AAP_LATEST_REVISION, blankToNull(latestRevision))
                    .set(AAP_PROJECTION_JSON, toJsonbOrNull(artifactProjection))
                    .set(AAP_SOURCE_RUN_ID, entry.getRunId())
                    .set(AAP_SOURCE_UPDATED_AT, projectedAt)
                    .set(AAP_EXPIRES_AT, (OffsetDateTime) null)
                    .execute();

            ArrayNode views = activeViews(snapshot, 20, false);
            for (JsonNode view : views) {
                upsertArtifactView(entry, sessionId, projectId, scopeKey, projectedAt, view);
            }
        } catch (Exception e) {
            log.warn("Persist Agent projection state failed: runId={}, error={}", entry.getRunId(), e.getMessage());
        }
    }

    private void upsertArtifactView(LedgerSnapshotEntry entry,
                                    long sessionId,
                                    Long projectId,
                                    String scopeKey,
                                    OffsetDateTime projectedAt,
                                    JsonNode view) {
        if (view instanceof ObjectNode viewObject) {
            mergePathAwareStatusFromStoredView(entry.getUserId(), sessionId, scopeKey, viewObject);
        }
        String viewKey = firstNonBlank(text(view.path("viewId")), text(view.path("callId")));
        if (viewKey.isBlank()) {
            return;
        }
        dsl.insertInto(AGENT_ARTIFACT_VIEWS)
                .set(AAV_USER_ID, entry.getUserId())
                .set(AAV_SESSION_ID, sessionId)
                .set(AAV_PROJECT_ID, projectId)
                .set(AAV_PROJECT_SCOPE_KEY, scopeKey)
                .set(AAV_VIEW_KEY, viewKey)
                .set(AAV_DOCUMENT_ID, positiveLong(view.path("documentId")))
                .set(AAV_FILE_ID, positiveLong(view.path("fileId")))
                .set(AAV_TOOL_NAME, blankToNull(text(view.path("tool"))))
                .set(AAV_VIEW_TYPE, blankToNull(text(view.path("viewType"))))
                .set(AAV_READ_KEY, blankToNull(viewKey))
                .set(AAV_REVISION, blankToNull(firstNonBlank(text(view.path("revision")), text(view.path("readKeyRevision")))))
                .set(AAV_STATUS, blankToNull(text(view.path("status"))))
                .set(AAV_RESULT_CHARS, view.path("resultChars").canConvertToInt() ? view.path("resultChars").asInt() : null)
                .set(AAV_PROJECTION_JSON, toJsonbOrNull(view))
                .set(AAV_SOURCE_RUN_ID, entry.getRunId())
                .set(AAV_SOURCE_CALL_ID, blankToNull(text(view.path("callId"))))
                .set(AAV_SOURCE_UPDATED_AT, projectedAt)
                .set(AAV_EXPIRES_AT, (OffsetDateTime) null)
                .onConflict(AAV_USER_ID, AAV_SESSION_ID, AAV_PROJECT_SCOPE_KEY, AAV_VIEW_KEY)
                .doUpdate()
                .set(AAV_PROJECT_ID, projectId)
                .set(AAV_DOCUMENT_ID, positiveLong(view.path("documentId")))
                .set(AAV_FILE_ID, positiveLong(view.path("fileId")))
                .set(AAV_TOOL_NAME, blankToNull(text(view.path("tool"))))
                .set(AAV_VIEW_TYPE, blankToNull(text(view.path("viewType"))))
                .set(AAV_READ_KEY, blankToNull(viewKey))
                .set(AAV_REVISION, blankToNull(firstNonBlank(text(view.path("revision")), text(view.path("readKeyRevision")))))
                .set(AAV_STATUS, blankToNull(text(view.path("status"))))
                .set(AAV_RESULT_CHARS, view.path("resultChars").canConvertToInt() ? view.path("resultChars").asInt() : null)
                .set(AAV_PROJECTION_JSON, toJsonbOrNull(view))
                .set(AAV_SOURCE_RUN_ID, entry.getRunId())
                .set(AAV_SOURCE_CALL_ID, blankToNull(text(view.path("callId"))))
                .set(AAV_SOURCE_UPDATED_AT, projectedAt)
                .set(AAV_EXPIRES_AT, (OffsetDateTime) null)
                .execute();
    }

    private void mergePathAwareStatusesFromStoredViews(long userId,
                                                       long sessionId,
                                                       String scopeKey,
                                                       ObjectNode artifactProjection) {
        if (artifactProjection == null || !artifactProjection.path("activeViews").isArray()) {
            return;
        }
        ArrayNode views = (ArrayNode) artifactProjection.path("activeViews");
        for (JsonNode view : views) {
            if (view != null && view.isObject()) {
                mergePathAwareStatusFromStoredView(userId, sessionId, scopeKey, (ObjectNode) view);
            }
        }
        artifactProjection.set("viewStatusSummary", viewStatusSummary(views));
    }

    private void mergePathAwareStatusFromStoredView(long userId,
                                                    long sessionId,
                                                    String scopeKey,
                                                    ObjectNode incomingView) {
        String viewKey = firstNonBlank(text(incomingView.path("viewId")), text(incomingView.path("callId")));
        if (viewKey.isBlank()) {
            return;
        }
        try {
            var record = dsl.select(AAV_PROJECTION_JSON)
                    .from(AGENT_ARTIFACT_VIEWS)
                    .where(AAV_USER_ID.eq(userId)
                            .and(AAV_SESSION_ID.eq(sessionId))
                            .and(AAV_PROJECT_SCOPE_KEY.eq(scopeKey))
                            .and(AAV_VIEW_KEY.eq(viewKey))
                            .and(AAV_EXPIRES_AT.isNull().or(AAV_EXPIRES_AT.gt(OffsetDateTime.now()))))
                    .limit(1)
                    .fetchOne();
            if (record == null || record.get(AAV_PROJECTION_JSON) == null
                    || record.get(AAV_PROJECTION_JSON).data() == null
                    || record.get(AAV_PROJECTION_JSON).data().isBlank()) {
                return;
            }
            JsonNode stored = objectMapper.readTree(record.get(AAV_PROJECTION_JSON).data());
            if (canReuseStoredPathAwareStatus(stored, incomingView)) {
                copyField(incomingView, stored, "status");
                copyField(incomingView, stored, "statusReason");
                copyField(incomingView, stored, "latestDocumentRevision");
                copyField(incomingView, stored, "lastWriteImpact");
                copyField(incomingView, stored, "writeSafety");
            }
        } catch (Exception e) {
            log.debug("Stored path-aware view status merge skipped: viewKey={}, error={}", viewKey, e.getMessage());
        }
    }

    private static boolean canReuseStoredPathAwareStatus(JsonNode stored, JsonNode incoming) {
        if (stored == null || !stored.isObject() || incoming == null || !incoming.isObject()) {
            return false;
        }
        JsonNode impact = stored.path("lastWriteImpact");
        if (!impact.path("pathAware").asBoolean(false) || impact.path("overlapsView").asBoolean(true)) {
            return false;
        }
        if (!"latest".equals(text(stored.path("status")))) {
            return false;
        }
        String storedLatest = text(stored.path("latestDocumentRevision"));
        String incomingLatest = text(incoming.path("latestDocumentRevision"));
        if (!storedLatest.isBlank() && !incomingLatest.isBlank() && !storedLatest.equals(incomingLatest)) {
            return false;
        }
        String storedRevision = firstNonBlank(text(stored.path("revision")), text(stored.path("readKeyRevision")));
        String incomingRevision = firstNonBlank(text(incoming.path("revision")), text(incoming.path("readKeyRevision")));
        return storedRevision.isBlank() || incomingRevision.isBlank() || storedRevision.equals(incomingRevision);
    }

    private void applyArtifactWriteImpact(ToolCallEntry entry) {
        Optional<ArtifactWriteImpact> impactOpt = extractArtifactWriteImpact(entry);
        if (impactOpt.isEmpty()) {
            return;
        }
        Long userId = nullablePositive(entry.getUserId());
        Long sessionId = nullablePositive(entry.getSessionId());
        if (userId == null || sessionId == null) {
            return;
        }
        ArtifactWriteImpact impact = impactOpt.get();
        Long projectId = nullablePositive(entry.getProjectId());
        String scopeKey = projectionScopeKey(projectId);
        OffsetDateTime writeAt = entry.getFinishedAt() == null ? OffsetDateTime.now() : entry.getFinishedAt();
        try {
            updateArtifactProjectionAfterWrite(entry, impact, userId, sessionId, projectId, scopeKey, writeAt);
            int staleViews = refreshArtifactViewsAfterWrite(impact, userId, sessionId, scopeKey, writeAt);
            log.info("Applied Agent artifact write impact: runId={}, callId={}, tool={}, documentId={}, revision={}, paths={}, staleViews={}",
                    entry.getRunId(),
                    entry.getCallId(),
                    impact.toolName(),
                    impact.documentId(),
                    impact.newRevision(),
                    impact.modifiedPaths().size(),
                    staleViews);
        } catch (Exception e) {
            log.warn("Apply Agent artifact write impact failed: runId={}, callId={}, tool={}, error={}",
                    entry.getRunId(), entry.getCallId(), entry.getToolName(), e.getMessage());
        }
        try {
            upsertDocumentRevisionIndex(entry, impact, userId, sessionId, projectId, writeAt);
        } catch (Exception e) {
            log.warn("Sync agent_run_document_revisions failed: runId={}, callId={}, tool={}, documentId={}, error={}",
                    entry.getRunId(), entry.getCallId(), entry.getToolName(), impact.documentId(), e.getMessage());
        }
    }

    private void upsertSessionAgentRunFromRun(RunEntry entry) {
        if (entry == null) {
            return;
        }
        String runId = blankToNull(entry.getRunId());
        Long userId = nullablePositive(entry.getUserId());
        Long sessionId = nullablePositive(entry.getSessionId());
        if (runId == null || userId == null || sessionId == null) {
            return;
        }
        Long projectId = nullablePositive(entry.getProjectId());
        OffsetDateTime startedAt = entry.getStartedAt() == null ? OffsetDateTime.now() : entry.getStartedAt();
        dsl.insertInto(SESSION_AGENT_RUNS)
                .set(SAR_USER_ID, userId)
                .set(SAR_SESSION_ID, sessionId)
                .set(SAR_PROJECT_ID, projectId)
                .set(SAR_AGENT_RUN_ID, runId)
                .set(SAR_STATUS, normalizeSessionAgentRunStatus(entry.getStatus(), "running"))
                .set(SAR_MAPPING_SOURCE, "agent_runs_lifecycle")
                .set(SAR_STARTED_AT, startedAt)
                .set(SAR_METADATA, jsonbObjectOrEmpty(entry.getMetadataJson()))
                .onConflict(SAR_AGENT_RUN_ID)
                .doUpdate()
                .set(SAR_USER_ID, userId)
                .set(SAR_SESSION_ID, sessionId)
                .set(SAR_PROJECT_ID, projectId)
                .set(SAR_STATUS, normalizeSessionAgentRunStatus(entry.getStatus(), "running"))
                .set(SAR_STARTED_AT, startedAt)
                .set(SAR_METADATA, jsonbObjectOrEmpty(entry.getMetadataJson()))
                .execute();
    }

    private void finishSessionAgentRun(FinishRunEntry entry, OffsetDateTime endedAt) {
        if (entry == null || blankToNull(entry.getRunId()) == null) {
            return;
        }
        dsl.update(SESSION_AGENT_RUNS)
                .set(SAR_STATUS, normalizeSessionAgentRunStatus(entry.getStatus(), "completed"))
                .set(SAR_ENDED_AT, endedAt == null ? OffsetDateTime.now() : endedAt)
                .set(SAR_METADATA, jsonbObjectOrEmpty(entry.getMetadataJson()))
                .where(SAR_AGENT_RUN_ID.eq(entry.getRunId()))
                .execute();
    }

    private void markSessionAgentRunCancelled(String runId, OffsetDateTime cancelledAt) {
        String value = blankToNull(runId);
        if (value == null) {
            return;
        }
        dsl.update(SESSION_AGENT_RUNS)
                .set(SAR_STATUS, "cancelled")
                .set(SAR_ENDED_AT, cancelledAt == null ? OffsetDateTime.now() : cancelledAt)
                .where(SAR_AGENT_RUN_ID.eq(value))
                .execute();
    }

    private Long findLatestUserMessageIdBeforeSeq(long sessionId, int firstMessageSeq) {
        if (sessionId <= 0 || firstMessageSeq <= 0) {
            return null;
        }
        return dsl.select(SM_ID)
                .from(SESSION_MESSAGES)
                .where(SM_SESSION_ID.eq(sessionId)
                        .and(SM_SEQ.lt(firstMessageSeq))
                        .and(SM_ROLE.eq("user"))
                        .and(SM_IS_DELETED.eq(false)))
                .orderBy(SM_SEQ.desc())
                .limit(1)
                .fetchOne(SM_ID);
    }

    private void upsertDocumentRevisionIndex(ToolCallEntry entry,
                                             ArtifactWriteImpact impact,
                                             long userId,
                                             long sessionId,
                                             Long projectId,
                                             OffsetDateTime writeAt) throws Exception {
        String runId = blankToNull(entry.getRunId());
        Long documentId = nullablePositive(impact.documentId());
        if (runId == null || documentId == null) {
            return;
        }

        JsonNode args = parseJsonNode(entry.getArgumentsJson()).orElse(objectMapper.createObjectNode());
        JsonNode result = parseJsonNode(entry.getResultJson()).orElse(objectMapper.createObjectNode());
        RevisionValue beforeRevision = extractBeforeRevision(args, result);
        RevisionValue afterRevision = extractAfterRevision(impact, result);
        String documentType = extractDocumentType(args, result);
        String canonicalKind = firstNonBlank(extractCanonicalKind(args, result), inferCanonicalKind(entry.getToolName()));
        OffsetDateTime effectiveWriteAt = writeAt == null ? OffsetDateTime.now() : writeAt;

        var existing = dsl.select(
                        ARDR_BEFORE_REVISION,
                        ARDR_AFTER_REVISION,
                        ARDR_BEFORE_REVISION_SOURCE,
                        ARDR_AFTER_REVISION_SOURCE,
                        ARDR_FIRST_WRITE_CALL_ID,
                        ARDR_FIRST_WRITE_TOOL_NAME,
                        ARDR_WRITE_COUNT,
                        ARDR_MODIFIED_PATHS,
                        ARDR_TOUCHED_PAGES,
                        ARDR_DOCUMENT_TYPE,
                        ARDR_CANONICAL_KIND)
                .from(AGENT_RUN_DOCUMENT_REVISIONS)
                .where(ARDR_AGENT_RUN_ID.eq(runId).and(ARDR_DOCUMENT_ID.eq(documentId)))
                .limit(1)
                .fetchOne();

        if (existing == null) {
            dsl.insertInto(AGENT_RUN_DOCUMENT_REVISIONS)
                    .set(ARDR_USER_ID, userId)
                    .set(ARDR_SESSION_ID, sessionId)
                    .set(ARDR_PROJECT_ID, projectId)
                    .set(ARDR_AGENT_RUN_ID, runId)
                    .set(ARDR_DOCUMENT_ID, documentId)
                    .set(ARDR_DOCUMENT_TYPE, blankToNull(documentType))
                    .set(ARDR_CANONICAL_KIND, blankToNull(canonicalKind))
                    .set(ARDR_BEFORE_REVISION, blankToNull(beforeRevision.value()))
                    .set(ARDR_AFTER_REVISION, blankToNull(afterRevision.value()))
                    .set(ARDR_BEFORE_REVISION_SOURCE, blankToNull(beforeRevision.source()))
                    .set(ARDR_AFTER_REVISION_SOURCE, blankToNull(afterRevision.source()))
                    .set(ARDR_FIRST_WRITE_CALL_ID, blankToNull(impact.callId()))
                    .set(ARDR_LAST_WRITE_CALL_ID, blankToNull(impact.callId()))
                    .set(ARDR_FIRST_WRITE_TOOL_NAME, blankToNull(impact.toolName()))
                    .set(ARDR_LAST_WRITE_TOOL_NAME, blankToNull(impact.toolName()))
                    .set(ARDR_WRITE_COUNT, 1)
                    .set(ARDR_MODIFIED_PATHS, jsonbArrayFromStrings(impact.modifiedPaths()))
                    .set(ARDR_TOUCHED_PAGES, jsonbArrayFromIntegers(Set.of()))
                    .set(ARDR_STATUS, "running")
                    .set(ARDR_ROLLBACK_VISIBLE, false)
                    .set(ARDR_ROLLBACK_DISABLED_REASON, "run_not_finished")
                    .set(ARDR_SOURCE, "agent")
                    .set(ARDR_FIRST_WRITE_AT, effectiveWriteAt)
                    .set(ARDR_LAST_WRITE_AT, effectiveWriteAt)
                    .set(ARDR_METADATA, documentRevisionMetadata(entry, impact, beforeRevision, afterRevision))
                    .execute();
            return;
        }

        String existingBeforeRevision = Objects.toString(existing.get(ARDR_BEFORE_REVISION), "").trim();
        String nextBeforeRevision = firstNonBlank(existingBeforeRevision, beforeRevision.value());
        String nextBeforeSource = firstNonBlank(existing.get(ARDR_BEFORE_REVISION_SOURCE), beforeRevision.source());
        String nextAfterRevision = firstNonBlank(afterRevision.value(), existing.get(ARDR_AFTER_REVISION));
        String nextAfterSource = firstNonBlank(afterRevision.source(), existing.get(ARDR_AFTER_REVISION_SOURCE));
        String nextDocumentType = firstNonBlank(documentType, existing.get(ARDR_DOCUMENT_TYPE));
        String nextCanonicalKind = firstNonBlank(canonicalKind, existing.get(ARDR_CANONICAL_KIND));

        LinkedHashSet<String> mergedPaths = stringSetFromJsonb(existing.get(ARDR_MODIFIED_PATHS));
        impact.modifiedPaths().stream()
                .filter(path -> path != null && !path.isBlank())
                .limit(MAX_WRITE_IMPACT_PATHS)
                .forEach(mergedPaths::add);
        List<String> cappedPaths = mergedPaths.stream().limit(MAX_WRITE_IMPACT_PATHS).toList();

        int writeCount = Math.max(0, Optional.ofNullable(existing.get(ARDR_WRITE_COUNT)).orElse(0)) + 1;
        dsl.update(AGENT_RUN_DOCUMENT_REVISIONS)
                .set(ARDR_USER_ID, userId)
                .set(ARDR_SESSION_ID, sessionId)
                .set(ARDR_PROJECT_ID, projectId)
                .set(ARDR_DOCUMENT_TYPE, blankToNull(nextDocumentType))
                .set(ARDR_CANONICAL_KIND, blankToNull(nextCanonicalKind))
                .set(ARDR_BEFORE_REVISION, blankToNull(nextBeforeRevision))
                .set(ARDR_AFTER_REVISION, blankToNull(nextAfterRevision))
                .set(ARDR_BEFORE_REVISION_SOURCE, blankToNull(nextBeforeSource))
                .set(ARDR_AFTER_REVISION_SOURCE, blankToNull(nextAfterSource))
                .set(ARDR_FIRST_WRITE_CALL_ID, blankToNull(firstNonBlank(existing.get(ARDR_FIRST_WRITE_CALL_ID), impact.callId())))
                .set(ARDR_LAST_WRITE_CALL_ID, blankToNull(impact.callId()))
                .set(ARDR_FIRST_WRITE_TOOL_NAME, blankToNull(firstNonBlank(existing.get(ARDR_FIRST_WRITE_TOOL_NAME), impact.toolName())))
                .set(ARDR_LAST_WRITE_TOOL_NAME, blankToNull(impact.toolName()))
                .set(ARDR_WRITE_COUNT, writeCount)
                .set(ARDR_MODIFIED_PATHS, jsonbArrayFromStrings(cappedPaths))
                .set(ARDR_TOUCHED_PAGES, jsonbArrayFromIntegers(Set.of()))
                .set(ARDR_STATUS, "running")
                .set(ARDR_ROLLBACK_VISIBLE, false)
                .set(ARDR_ROLLBACK_DISABLED_REASON, "run_not_finished")
                .set(ARDR_LAST_WRITE_AT, effectiveWriteAt)
                .set(ARDR_METADATA, documentRevisionMetadata(entry, impact, beforeRevision, afterRevision))
                .where(ARDR_AGENT_RUN_ID.eq(runId).and(ARDR_DOCUMENT_ID.eq(documentId)))
                .execute();
    }

    private void finishDocumentRevisionIndexes(String runId, OffsetDateTime completedAt) {
        String value = blankToNull(runId);
        if (value == null) {
            return;
        }
        OffsetDateTime effectiveCompletedAt = completedAt == null ? OffsetDateTime.now() : completedAt;
        var records = dsl.select(
                        ARDR_USER_ID,
                        ARDR_SESSION_ID,
                        ARDR_PROJECT_ID,
                        ARDR_DOCUMENT_ID,
                        ARDR_DOCUMENT_TYPE,
                        ARDR_CANONICAL_KIND,
                        ARDR_BEFORE_REVISION,
                        ARDR_AFTER_REVISION,
                        ARDR_FIRST_WRITE_CALL_ID,
                        ARDR_LAST_WRITE_CALL_ID,
                        ARDR_FIRST_WRITE_TOOL_NAME,
                        ARDR_LAST_WRITE_TOOL_NAME,
                        ARDR_WRITE_COUNT,
                        ARDR_MODIFIED_PATHS,
                        ARDR_TOUCHED_PAGES)
                .from(AGENT_RUN_DOCUMENT_REVISIONS)
                .where(ARDR_AGENT_RUN_ID.eq(value).and(ARDR_IS_DELETED.eq(false)))
                .fetch();
        for (var record : records) {
            Long documentId = record.get(ARDR_DOCUMENT_ID);
            String beforeRevision = Objects.toString(record.get(ARDR_BEFORE_REVISION), "").trim();
            String afterRevision = Objects.toString(record.get(ARDR_AFTER_REVISION), "").trim();
            boolean hasBefore = !beforeRevision.isBlank();
            boolean hasAfter = !afterRevision.isBlank();
            boolean changed = hasBefore && hasAfter && !beforeRevision.equals(afterRevision);
            String disabledReason;
            if (changed) {
                disabledReason = null;
            } else if (!hasBefore || !hasAfter) {
                disabledReason = "missing_revision";
            } else {
                disabledReason = "no_revision_change";
            }
            dsl.update(AGENT_RUN_DOCUMENT_REVISIONS)
                    .set(ARDR_STATUS, changed ? "changed" : "unchanged")
                    .set(ARDR_ROLLBACK_VISIBLE, changed)
                    .set(ARDR_ROLLBACK_DISABLED_REASON, disabledReason)
                    .set(ARDR_COMPLETED_AT, effectiveCompletedAt)
                    .where(ARDR_AGENT_RUN_ID.eq(value).and(ARDR_DOCUMENT_ID.eq(documentId)))
                    .execute();
            if (changed) {
                try {
                    appendAgentWriteRevisionEvent(value, record);
                } catch (Exception e) {
                    log.warn("Append agent_write document revision event failed: runId={}, documentId={}, error={}",
                            value, documentId, e.getMessage());
                }
            }
        }
    }

    private void appendAgentWriteRevisionEvent(String runId, org.jooq.Record record) {
        Long userId = nullablePositive(record.get(ARDR_USER_ID));
        Long sessionId = nullablePositive(record.get(ARDR_SESSION_ID));
        Long documentId = nullablePositive(record.get(ARDR_DOCUMENT_ID));
        if (blankToNull(runId) == null || userId == null || sessionId == null || documentId == null) {
            return;
        }
        lockDocumentRevisionEvents(documentId);
        if (agentWriteRevisionEventExists(runId, documentId)) {
            return;
        }
        String lastToolName = firstNonBlank(record.get(ARDR_LAST_WRITE_TOOL_NAME), record.get(ARDR_FIRST_WRITE_TOOL_NAME));
        String lastCallId = firstNonBlank(record.get(ARDR_LAST_WRITE_CALL_ID), record.get(ARDR_FIRST_WRITE_CALL_ID));
        dsl.insertInto(DOCUMENT_REVISION_EVENTS)
                .set(DRE_USER_ID, userId)
                .set(DRE_PROJECT_ID, nullablePositive(record.get(ARDR_PROJECT_ID)))
                .set(DRE_DOCUMENT_ID, documentId)
                .set(DRE_DOCUMENT_TYPE, blankToNull(record.get(ARDR_DOCUMENT_TYPE)))
                .set(DRE_CANONICAL_KIND, blankToNull(record.get(ARDR_CANONICAL_KIND)))
                .set(DRE_DOCUMENT_EVENT_SEQ, nextDocumentRevisionEventSeq(documentId))
                .set(DRE_EVENT_TYPE, "agent_write")
                .set(DRE_ACTOR_TYPE, "agent")
                .set(DRE_TRIGGER_SOURCE, "agent_tool")
                .set(DRE_STATUS, "committed")
                .set(DRE_FROM_REVISION, blankToNull(record.get(ARDR_BEFORE_REVISION)))
                .set(DRE_TO_REVISION, blankToNull(record.get(ARDR_AFTER_REVISION)))
                .set(DRE_SOURCE_SESSION_ID, sessionId)
                .set(DRE_SOURCE_AGENT_RUN_ID, runId)
                .set(DRE_SOURCE_TOOL_CALL_ID, blankToNull(lastCallId))
                .set(DRE_SOURCE_TOOL_NAME, blankToNull(lastToolName))
                .set(DRE_MODIFIED_PATHS, jsonbArrayOrEmpty(record.get(ARDR_MODIFIED_PATHS)))
                .set(DRE_TOUCHED_PAGES, jsonbArrayOrEmpty(record.get(ARDR_TOUCHED_PAGES)))
                .set(DRE_SUMMARY, agentWriteEventSummary(record))
                .set(DRE_METADATA, agentWriteEventMetadata(runId, record))
                .set(DRE_IS_DELETED, false)
                .execute();
    }

    private boolean agentWriteRevisionEventExists(String runId, long documentId) {
        return dsl.fetchExists(
                dsl.selectOne()
                        .from(DOCUMENT_REVISION_EVENTS)
                        .where(DRE_SOURCE_AGENT_RUN_ID.eq(runId)
                                .and(DRE_DOCUMENT_ID.eq(documentId))
                                .and(DRE_EVENT_TYPE.eq("agent_write"))
                                .and(DRE_IS_DELETED.eq(false)))
        );
    }

    private void lockDocumentRevisionEvents(long documentId) {
        dsl.fetch("SELECT pg_advisory_xact_lock(?)", documentId);
    }

    private long nextDocumentRevisionEventSeq(long documentId) {
        Long nextSeq = dsl.select(DSL.field("COALESCE(MAX({0}), 0) + 1", Long.class, DRE_DOCUMENT_EVENT_SEQ))
                .from(DOCUMENT_REVISION_EVENTS)
                .where(DRE_DOCUMENT_ID.eq(documentId))
                .fetchOne(0, Long.class);
        return nextSeq == null || nextSeq <= 0 ? 1L : nextSeq;
    }

    private String agentWriteEventSummary(org.jooq.Record record) {
        Integer writeCount = record.get(ARDR_WRITE_COUNT);
        String lastToolName = firstNonBlank(record.get(ARDR_LAST_WRITE_TOOL_NAME), record.get(ARDR_FIRST_WRITE_TOOL_NAME));
        if (lastToolName.isBlank()) {
            return "Agent updated document content.";
        }
        return "Agent updated document content with " + lastToolName + " (" + Math.max(1, Optional.ofNullable(writeCount).orElse(1)) + " write(s)).";
    }

    private JSONB agentWriteEventMetadata(String runId, org.jooq.Record record) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("schemaVersion", 1);
        metadata.put("kind", "document_revision_event_agent_write");
        metadata.put("eventSource", "agent_run_document_revisions");
        putText(metadata, "agentRunId", runId);
        putText(metadata, "firstWriteCallId", record.get(ARDR_FIRST_WRITE_CALL_ID));
        putText(metadata, "lastWriteCallId", record.get(ARDR_LAST_WRITE_CALL_ID));
        putText(metadata, "firstWriteToolName", record.get(ARDR_FIRST_WRITE_TOOL_NAME));
        putText(metadata, "lastWriteToolName", record.get(ARDR_LAST_WRITE_TOOL_NAME));
        metadata.put("writeCount", Math.max(0, Optional.ofNullable(record.get(ARDR_WRITE_COUNT)).orElse(0)));
        JSONB jsonb = toJsonbOrNull(metadata);
        return jsonb == null ? org.jooq.JSONB.valueOf("{}") : jsonb;
    }

    private void updateArtifactProjectionAfterWrite(ToolCallEntry entry,
                                                    ArtifactWriteImpact impact,
                                                    long userId,
                                                    long sessionId,
                                                    Long projectId,
                                                    String scopeKey,
                                                    OffsetDateTime writeAt) throws Exception {
        Condition condition = AAP_USER_ID.eq(userId)
                .and(AAP_SESSION_ID.eq(sessionId))
                .and(AAP_PROJECT_SCOPE_KEY.eq(scopeKey))
                .and(AAP_ARTIFACT_KEY.eq("session_artifacts"));

        var record = dsl.select(AAP_PROJECTION_JSON, AAP_FILE_ID)
                .from(AGENT_ARTIFACT_PROJECTION)
                .where(condition)
                .limit(1)
                .fetchOne();

        ObjectNode projection;
        Long existingFileId = null;
        if (record == null || record.get(AAP_PROJECTION_JSON) == null
                || record.get(AAP_PROJECTION_JSON).data() == null
                || record.get(AAP_PROJECTION_JSON).data().isBlank()) {
            projection = newArtifactProjectionFromWriteImpact(entry, impact, writeAt);
        } else {
            JsonNode parsed = objectMapper.readTree(record.get(AAP_PROJECTION_JSON).data());
            projection = parsed.isObject() ? (ObjectNode) parsed.deepCopy()
                    : newArtifactProjectionFromWriteImpact(entry, impact, writeAt);
            existingFileId = record.get(AAP_FILE_ID);
        }

        Long fileId = impact.fileId() == null ? existingFileId : impact.fileId();
        touchArtifactProjectionAfterWrite(projection, impact.withFileId(fileId), writeAt);
        String latestRevision = firstNonBlank(
                impact.newRevision(),
                firstNonBlank(text(projection.path("activeDocument").path("revision")),
                        text(projection.path("activeDocument").path("latestDocumentRevision")))
        );

        dsl.insertInto(AGENT_ARTIFACT_PROJECTION)
                .set(AAP_USER_ID, userId)
                .set(AAP_SESSION_ID, sessionId)
                .set(AAP_PROJECT_ID, projectId)
                .set(AAP_PROJECT_SCOPE_KEY, scopeKey)
                .set(AAP_ARTIFACT_KEY, "session_artifacts")
                .set(AAP_DOCUMENT_ID, impact.documentId())
                .set(AAP_FILE_ID, fileId)
                .set(AAP_LATEST_REVISION, blankToNull(latestRevision))
                .set(AAP_PROJECTION_JSON, toJsonbOrNull(projection))
                .set(AAP_SOURCE_RUN_ID, entry.getRunId())
                .set(AAP_SOURCE_UPDATED_AT, writeAt)
                .set(AAP_EXPIRES_AT, (OffsetDateTime) null)
                .onConflict(AAP_USER_ID, AAP_SESSION_ID, AAP_PROJECT_SCOPE_KEY, AAP_ARTIFACT_KEY)
                .doUpdate()
                .set(AAP_PROJECT_ID, projectId)
                .set(AAP_DOCUMENT_ID, impact.documentId())
                .set(AAP_FILE_ID, fileId)
                .set(AAP_LATEST_REVISION, blankToNull(latestRevision))
                .set(AAP_PROJECTION_JSON, toJsonbOrNull(projection))
                .set(AAP_SOURCE_RUN_ID, entry.getRunId())
                .set(AAP_SOURCE_UPDATED_AT, writeAt)
                .set(AAP_EXPIRES_AT, (OffsetDateTime) null)
                .execute();
    }

    private int refreshArtifactViewsAfterWrite(ArtifactWriteImpact impact,
                                               long userId,
                                               long sessionId,
                                               String scopeKey,
                                               OffsetDateTime writeAt) throws Exception {
        var records = dsl.select(AAV_VIEW_KEY, AAV_PROJECTION_JSON)
                .from(AGENT_ARTIFACT_VIEWS)
                .where(AAV_USER_ID.eq(userId)
                        .and(AAV_SESSION_ID.eq(sessionId))
                        .and(AAV_PROJECT_SCOPE_KEY.eq(scopeKey))
                        .and(AAV_DOCUMENT_ID.eq(impact.documentId()))
                        .and(AAV_EXPIRES_AT.isNull().or(AAV_EXPIRES_AT.gt(OffsetDateTime.now()))))
                .fetch();

        int staleViews = 0;
        for (var record : records) {
            JSONB projectionJson = record.get(AAV_PROJECTION_JSON);
            if (projectionJson == null || projectionJson.data() == null || projectionJson.data().isBlank()) {
                continue;
            }
            JsonNode parsed = objectMapper.readTree(projectionJson.data());
            if (!parsed.isObject()) {
                continue;
            }
            ObjectNode view = (ObjectNode) parsed.deepCopy();
            boolean stale = applyWriteImpactToView(view, impact, writeAt);
            if (stale) {
                staleViews++;
            }
            dsl.update(AGENT_ARTIFACT_VIEWS)
                    .set(AAV_STATUS, blankToNull(text(view.path("status"))))
                    .set(AAV_PROJECTION_JSON, toJsonbOrNull(view))
                    .where(AAV_USER_ID.eq(userId)
                            .and(AAV_SESSION_ID.eq(sessionId))
                            .and(AAV_PROJECT_SCOPE_KEY.eq(scopeKey))
                            .and(AAV_VIEW_KEY.eq(record.get(AAV_VIEW_KEY))))
                    .execute();
        }
        return staleViews;
    }

    private ObjectNode newArtifactProjectionFromWriteImpact(ToolCallEntry entry,
                                                            ArtifactWriteImpact impact,
                                                            OffsetDateTime writeAt) {
        ObjectNode projection = objectMapper.createObjectNode();
        projection.put("kind", "persisted_agent_artifact_projection");
        projection.put("schemaVersion", 1);
        ObjectNode source = projection.putObject("source");
        putText(source, "agentRunId", entry.getRunId());
        putText(source, "updatedAt", writeAt == null ? null : writeAt.toString());
        projection.set("activeViews", objectMapper.createArrayNode());
        projection.set("readReuseHints", objectMapper.createArrayNode());
        projection.set("viewStatusSummary", viewStatusSummary(objectMapper.createArrayNode()));
        touchArtifactProjectionAfterWrite(projection, impact, writeAt);
        return projection;
    }

    void touchArtifactProjectionAfterWrite(ObjectNode projection,
                                           ArtifactWriteImpact impact,
                                           OffsetDateTime writeAt) {
        if (projection == null || impact == null) {
            return;
        }
        putText(projection, "kind", firstNonBlank(text(projection.path("kind")),
                "persisted_agent_artifact_projection"));
        if (!projection.path("schemaVersion").canConvertToInt()) {
            projection.put("schemaVersion", 1);
        }

        ObjectNode source = projection.path("source").isObject()
                ? (ObjectNode) projection.path("source")
                : projection.putObject("source");
        putText(source, "updatedAt", writeAt == null ? null : writeAt.toString());
        putText(source, "lastWriteAt", writeAt == null ? null : writeAt.toString());

        ObjectNode activeDocument = projection.path("activeDocument").isObject()
                ? (ObjectNode) projection.path("activeDocument")
                : projection.putObject("activeDocument");
        if (!activeDocument.path("documentId").canConvertToLong()
                || activeDocument.path("documentId").asLong() == impact.documentId()) {
            activeDocument.put("documentId", impact.documentId());
            if (impact.fileId() != null) {
                activeDocument.put("fileId", impact.fileId());
            }
            putText(activeDocument, "revision", impact.newRevision());
            putText(activeDocument, "latestDocumentRevision", impact.newRevision());
            putText(activeDocument, "sourceKind", "write_tool");
            putText(activeDocument, "callId", impact.callId());
        }

        upsertLatestDocumentRevision(projection, impact);

        ArrayNode views = projection.path("activeViews").isArray()
                ? (ArrayNode) projection.path("activeViews")
                : objectMapper.createArrayNode();
        for (JsonNode view : views) {
            if (view != null && view.isObject()
                    && positiveLong(view.path("documentId")) != null
                    && positiveLong(view.path("documentId")).equals(impact.documentId())) {
                applyWriteImpactToView((ObjectNode) view, impact, writeAt);
            }
        }
        projection.set("activeViews", views);
        projection.set("viewStatusSummary", viewStatusSummary(views));

        ObjectNode lastWrite = projection.putObject("lastWrite");
        putText(lastWrite, "tool", impact.toolName());
        putText(lastWrite, "callId", impact.callId());
        lastWrite.put("documentId", impact.documentId());
        if (impact.fileId() != null) {
            lastWrite.put("fileId", impact.fileId());
        }
        putText(lastWrite, "newRevision", impact.newRevision());
        putText(lastWrite, "writtenAt", writeAt == null ? null : writeAt.toString());
        ArrayNode paths = lastWrite.putArray("modifiedPaths");
        impact.modifiedPaths().forEach(paths::add);
    }

    private void upsertLatestDocumentRevision(ObjectNode projection, ArtifactWriteImpact impact) {
        ArrayNode next = objectMapper.createArrayNode();
        boolean updated = false;
        JsonNode current = projection.path("latestDocumentRevisions");
        if (current.isArray()) {
            for (JsonNode item : current) {
                if (!item.isObject()) {
                    continue;
                }
                ObjectNode copy = (ObjectNode) item.deepCopy();
                Long documentId = positiveLong(copy.path("documentId"));
                if (documentId != null && documentId.equals(impact.documentId())) {
                    putText(copy, "revision", impact.newRevision());
                    putText(copy, "latestDocumentRevision", impact.newRevision());
                    putText(copy, "sourceKind", "write_tool");
                    putText(copy, "callId", impact.callId());
                    updated = true;
                }
                next.add(copy);
            }
        }
        if (!updated) {
            ObjectNode item = objectMapper.createObjectNode();
            item.put("documentId", impact.documentId());
            if (impact.fileId() != null) {
                item.put("fileId", impact.fileId());
            }
            putText(item, "revision", impact.newRevision());
            putText(item, "latestDocumentRevision", impact.newRevision());
            putText(item, "sourceKind", "write_tool");
            putText(item, "callId", impact.callId());
            next.add(item);
        }
        projection.set("latestDocumentRevisions", next);
    }

    boolean applyWriteImpactToView(ObjectNode view, ArtifactWriteImpact impact, OffsetDateTime writeAt) {
        if (view == null || impact == null) {
            return false;
        }
        putText(view, "latestDocumentRevision", impact.newRevision());
        String viewRevision = firstNonBlank(text(view.path("revision")), text(view.path("readKeyRevision")));
        boolean revisionMatchesLatest = !impact.newRevision().isBlank() && impact.newRevision().equals(viewRevision);
        boolean touched = !revisionMatchesLatest && viewTouchedByWrite(view, impact.modifiedPaths());
        ObjectNode marker = view.putObject("lastWriteImpact");
        putText(marker, "tool", impact.toolName());
        putText(marker, "callId", impact.callId());
        marker.put("documentId", impact.documentId());
        putText(marker, "newRevision", impact.newRevision());
        putText(marker, "checkedAt", writeAt == null ? null : writeAt.toString());
        marker.put("pathAware", true);
        marker.put("overlapsView", touched);
        ArrayNode paths = marker.putArray("modifiedPaths");
        impact.modifiedPaths().forEach(paths::add);

        if (revisionMatchesLatest) {
            view.put("status", "latest");
            view.put("statusReason", "view_revision_matches_latest_document_revision");
            return false;
        }
        if (touched) {
            view.put("status", "stale");
            view.put("statusReason", impact.modifiedPaths().isEmpty()
                    ? "write_tool_modified_document_unknown_path"
                    : "write_tool_modified_overlapping_path");
            view.put("writeSafety", "requires_latest_revision_before_write");
            return true;
        }
        if (!impact.newRevision().isBlank()) {
            view.put("status", "latest");
            view.put("statusReason", "last_write_did_not_overlap_view_path");
            view.put("writeSafety", "requires_latest_revision_before_write");
        } else {
            view.put("status", "unknown");
            view.put("statusReason", "write_tool_revision_unknown");
        }
        return false;
    }

    public Optional<String> findLatestLedgerProjectionJson(long userId,
                                                           long sessionId,
                                                           Long projectId,
                                                           OffsetDateTime minUpdatedAt,
                                                          int maxChars) {
        return findLatestLedgerSnapshot(userId, sessionId, projectId, minUpdatedAt)
                .flatMap(snapshot -> compactLedgerSnapshotJson(snapshot.rawJson(), snapshot.updatedAt(), maxChars));
    }

    public Optional<String> findLatestTaskProjectionJson(long userId,
                                                        long sessionId,
                                                        Long projectId,
                                                        OffsetDateTime minUpdatedAt,
                                                        int maxChars) {
        return findLatestStoredTaskProjectionJson(userId, sessionId, projectId, minUpdatedAt, maxChars)
                .or(() -> findLatestLedgerSnapshot(userId, sessionId, projectId, minUpdatedAt)
                        .flatMap(snapshot -> compactTaskProjectionJson(
                                snapshot.rawJson(), snapshot.updatedAt(), maxChars)));
    }

    public Optional<String> findLatestArtifactProjectionJson(long userId,
                                                            long sessionId,
                                                            Long projectId,
                                                            OffsetDateTime minUpdatedAt,
                                                            int maxChars) {
        return findLatestStoredArtifactProjectionJson(userId, sessionId, projectId, minUpdatedAt, maxChars)
                .or(() -> findLatestLedgerSnapshot(userId, sessionId, projectId, minUpdatedAt)
                        .flatMap(snapshot -> compactArtifactProjectionJson(
                                snapshot.rawJson(), snapshot.updatedAt(), maxChars)));
    }

    public Optional<String> findLatestLedgerSnapshotJson(long userId, long sessionId, int maxChars) {
        return findLatestLedgerProjectionJson(userId, sessionId, null, null, maxChars);
    }

    public Optional<String> openArtifactViewJson(long userId,
                                                 long sessionId,
                                                 Long projectId,
                                                 String viewKey,
                                                 Long documentId,
                                                 String viewType,
                                                 String mode,
                                                 String path,
                                                 int maxChars) {
        if (userId <= 0 || sessionId <= 0) {
            return Optional.empty();
        }
        String normalizedMode = normalizeArtifactViewMode(mode);
        int budget = clamp(maxChars, 500, 60_000, 8_000);
        try {
            Optional<ArtifactViewRecord> viewRecord = findArtifactView(
                    userId,
                    sessionId,
                    projectId,
                    viewKey,
                    nullablePositive(documentId),
                    viewType
            );
            if (viewRecord.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(renderArtifactViewOpenResult(
                    viewRecord.get(),
                    normalizedMode,
                    Objects.toString(path, "").trim(),
                    budget
            ));
        } catch (Exception e) {
            log.warn("Open Agent artifact view failed: userId={}, sessionId={}, viewKey={}, documentId={}, error={}",
                    userId, sessionId, viewKey, documentId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ArtifactViewRecord> findArtifactView(long userId,
                                                          long sessionId,
                                                          Long projectId,
                                                          String viewKey,
                                                          Long documentId,
                                                          String viewType) {
        String key = Objects.toString(viewKey, "").trim();
        Long docId = nullablePositive(documentId);
        String type = Objects.toString(viewType, "").trim();
        if (key.isBlank() && docId == null) {
            return Optional.empty();
        }
        Condition condition = AAV_USER_ID.eq(userId)
                .and(AAV_SESSION_ID.eq(sessionId))
                .and(AAV_PROJECT_SCOPE_KEY.eq(projectionScopeKey(projectId)))
                .and(AAV_EXPIRES_AT.isNull().or(AAV_EXPIRES_AT.gt(OffsetDateTime.now())));
        if (!key.isBlank()) {
            condition = condition.and(AAV_VIEW_KEY.eq(key));
        } else {
            condition = condition.and(AAV_DOCUMENT_ID.eq(docId));
            if (!type.isBlank()) {
                condition = condition.and(AAV_VIEW_TYPE.eq(type));
            }
        }

        var record = dsl.select(
                        AAV_VIEW_KEY,
                        AAV_DOCUMENT_ID,
                        AAV_FILE_ID,
                        AAV_TOOL_NAME,
                        AAV_VIEW_TYPE,
                        AAV_READ_KEY,
                        AAV_REVISION,
                        AAV_STATUS,
                        AAV_RESULT_CHARS,
                        AAV_PROJECTION_JSON,
                        AAV_SOURCE_RUN_ID,
                        AAV_SOURCE_CALL_ID,
                        AAV_SOURCE_UPDATED_AT,
                        AAV_UPDATED_AT
                )
                .from(AGENT_ARTIFACT_VIEWS)
                .where(condition)
                .orderBy(AAV_UPDATED_AT.desc())
                .limit(1)
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        JSONB projection = record.get(AAV_PROJECTION_JSON);
        if (projection == null || projection.data() == null || projection.data().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ArtifactViewRecord(
                record.get(AAV_VIEW_KEY),
                record.get(AAV_DOCUMENT_ID),
                record.get(AAV_FILE_ID),
                record.get(AAV_TOOL_NAME),
                record.get(AAV_VIEW_TYPE),
                record.get(AAV_READ_KEY),
                record.get(AAV_REVISION),
                record.get(AAV_STATUS),
                record.get(AAV_RESULT_CHARS),
                projection.data(),
                record.get(AAV_SOURCE_RUN_ID),
                record.get(AAV_SOURCE_CALL_ID),
                record.get(AAV_SOURCE_UPDATED_AT),
                record.get(AAV_UPDATED_AT)
        ));
    }

    private String renderArtifactViewOpenResult(ArtifactViewRecord view,
                                                String mode,
                                                String path,
                                                int maxChars) throws Exception {
        JsonNode projection = objectMapper.readTree(view.projectionJson());
        Optional<ToolResultRecord> toolResult = findToolResult(view.sourceRunId(), view.sourceCallId());
        Optional<JsonNode> rawResult = toolResult.flatMap(record -> parseJsonNode(record.resultJson()));

        ObjectNode out = objectMapper.createObjectNode();
        out.put("success", true);
        out.put("kind", "agent_artifact_view_open_result");
        out.put("schemaVersion", 1);
        out.put("mode", mode);
        putText(out, "view_key", view.viewKey());
        putText(out, "read_key", view.readKey());
        if (view.documentId() != null) {
            out.put("document_id", view.documentId());
        }
        if (view.fileId() != null) {
            out.put("file_id", view.fileId());
        }
        putText(out, "tool", view.toolName());
        putText(out, "view_type", view.viewType());
        putText(out, "revision", view.revision());
        putText(out, "status", view.status());
        if (view.resultChars() != null) {
            out.put("result_chars", view.resultChars());
        }
        ObjectNode source = out.putObject("source");
        putText(source, "run_id", view.sourceRunId());
        putText(source, "call_id", view.sourceCallId());
        if (view.sourceUpdatedAt() != null) {
            source.put("source_updated_at", view.sourceUpdatedAt().toString());
        }
        if (view.updatedAt() != null) {
            source.put("view_updated_at", view.updatedAt().toString());
        }

        out.set("view_projection", projection);
        out.put("detail_available", rawResult.isPresent());
        out.put("detail_source", rawResult.isPresent() ? "agent_tool_results" : "projection_only");
        toolResult.ifPresent(record -> {
            ObjectNode detail = out.putObject("detail_metadata");
            putText(detail, "tool", record.toolName());
            if (record.resultChars() != null) {
                detail.put("result_chars", record.resultChars());
            }
            detail.put("compacted_for_model", Boolean.TRUE.equals(record.compactedForModel()));
            if (record.storedAt() != null) {
                detail.put("stored_at", record.storedAt().toString());
            }
        });

        if ("detail".equals(mode)) {
            JsonNode value = rawResult.orElse(projection);
            putCappedJson(out, "value", value, maxChars);
        } else if ("path".equals(mode)) {
            JsonNode sourceNode = rawResult.orElse(projection);
            String pointer = normalizeJsonPointer(path);
            out.put("path", pointer);
            JsonNode value = sourceNode.at(pointer);
            if (value == null || value.isMissingNode()) {
                out.put("path_found", false);
            } else {
                out.put("path_found", true);
                putCappedJson(out, "value", value, maxChars);
            }
        }

        if (rawResult.isEmpty()) {
            out.set("reopen_hint", buildArtifactViewReopenHint(view));
        }
        return objectMapper.writeValueAsString(out);
    }

    private Optional<ToolResultRecord> findToolResult(String runId, String callId) {
        String rid = Objects.toString(runId, "").trim();
        String cid = Objects.toString(callId, "").trim();
        if (rid.isBlank() || cid.isBlank()) {
            return Optional.empty();
        }
        var record = dsl.select(ATR_TOOL_NAME, ATR_RESULT_JSON, ATR_RESULT_CHARS, ATR_COMPACTED_FOR_MODEL, ATR_STORED_AT)
                .from(AGENT_TOOL_RESULTS)
                .where(ATR_RUN_ID.eq(rid).and(ATR_CALL_ID.eq(cid)))
                .limit(1)
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        JSONB resultJson = record.get(ATR_RESULT_JSON);
        if (resultJson == null || resultJson.data() == null || resultJson.data().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ToolResultRecord(
                record.get(ATR_TOOL_NAME),
                resultJson.data(),
                record.get(ATR_RESULT_CHARS),
                record.get(ATR_COMPACTED_FOR_MODEL),
                record.get(ATR_STORED_AT)
        ));
    }

    private Optional<JsonNode> parseJsonNode(String raw) {
        try {
            return Optional.of(objectMapper.readTree(Objects.toString(raw, "")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private ObjectNode buildArtifactViewReopenHint(ArtifactViewRecord view) {
        ObjectNode hint = objectMapper.createObjectNode();
        hint.put("reason", "original_tool_result_not_available");
        hint.put("action", "call_narrow_read_tool");
        putText(hint, "suggested_tool", view.toolName());
        putText(hint, "view_type", view.viewType());
        if (view.documentId() != null) {
            hint.put("document_id", view.documentId());
        }
        if (view.fileId() != null) {
            hint.put("file_id", view.fileId());
        }
        putText(hint, "path", safeTextFromProjection(view.projectionJson(), "path"));
        putText(hint, "window", safeTextFromProjection(view.projectionJson(), "window"));
        putText(hint, "revision", view.revision());
        return hint;
    }

    private String safeTextFromProjection(String rawProjection, String field) {
        try {
            return text(objectMapper.readTree(Objects.toString(rawProjection, "")).path(field));
        } catch (Exception ignored) {
            return "";
        }
    }

    private Optional<String> findLatestStoredTaskProjectionJson(long userId,
                                                               long sessionId,
                                                               Long projectId,
                                                               OffsetDateTime minUpdatedAt,
                                                               int maxChars) {
        return findLatestStoredProjection(
                AGENT_TASK_PROGRESS,
                ATP_USER_ID,
                ATP_SESSION_ID,
                ATP_PROJECT_SCOPE_KEY,
                ATP_TASK_KEY,
                "default",
                ATP_PROJECTION_JSON,
                ATP_UPDATED_AT,
                ATP_EXPIRES_AT,
                userId,
                sessionId,
                projectId,
                minUpdatedAt
        ).flatMap(record -> compactStoredProjectionJson(record.rawJson(), record.updatedAt(), maxChars));
    }

    private Optional<String> findLatestStoredArtifactProjectionJson(long userId,
                                                                   long sessionId,
                                                                   Long projectId,
                                                                   OffsetDateTime minUpdatedAt,
                                                                   int maxChars) {
        return findLatestStoredProjection(
                AGENT_ARTIFACT_PROJECTION,
                AAP_USER_ID,
                AAP_SESSION_ID,
                AAP_PROJECT_SCOPE_KEY,
                AAP_ARTIFACT_KEY,
                "session_artifacts",
                AAP_PROJECTION_JSON,
                AAP_UPDATED_AT,
                AAP_EXPIRES_AT,
                userId,
                sessionId,
                projectId,
                minUpdatedAt
        ).flatMap(record -> compactStoredProjectionJson(record.rawJson(), record.updatedAt(), maxChars));
    }

    private Optional<ProjectionRecord> findLatestStoredProjection(Table<?> table,
                                                                  Field<Long> userField,
                                                                  Field<Long> sessionField,
                                                                  Field<String> scopeField,
                                                                  Field<String> keyField,
                                                                  String key,
                                                                  Field<JSONB> projectionField,
                                                                  Field<OffsetDateTime> updatedAtField,
                                                                  Field<OffsetDateTime> expiresAtField,
                                                                  long userId,
                                                                  long sessionId,
                                                                  Long projectId,
                                                                  OffsetDateTime minUpdatedAt) {
        if (userId <= 0 || sessionId <= 0) {
            return Optional.empty();
        }
        try {
            Condition condition = userField.eq(userId)
                    .and(sessionField.eq(sessionId))
                    .and(scopeField.eq(projectionScopeKey(projectId)))
                    .and(keyField.eq(key))
                    .and(expiresAtField.isNull().or(expiresAtField.gt(OffsetDateTime.now())));
            if (minUpdatedAt != null) {
                condition = condition.and(updatedAtField.ge(minUpdatedAt));
            }
            var record = dsl.select(projectionField, updatedAtField)
                    .from(table)
                    .where(condition)
                    .orderBy(updatedAtField.desc())
                    .limit(1)
                    .fetchOne();
            if (record == null) {
                return Optional.empty();
            }
            JSONB json = record.get(projectionField);
            if (json == null || json.data() == null || json.data().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new ProjectionRecord(json.data(), record.get(updatedAtField)));
        } catch (Exception e) {
            log.debug("Stored Agent projection unavailable, fallback to ledger snapshot: table={}, error={}",
                    table.getName(), e.getMessage());
            return Optional.empty();
        }
    }

    Optional<String> compactStoredProjectionJson(String raw, OffsetDateTime updatedAt, int maxChars) {
        int budget = Math.max(maxChars, 0);
        if (budget <= 0) {
            return Optional.empty();
        }
        String value = Objects.toString(raw, "").trim();
        if (value.isBlank()) {
            return Optional.empty();
        }
        if (value.length() <= budget) {
            return Optional.of(value);
        }
        try {
            JsonNode projection = objectMapper.readTree(value);
            String kind = text(projection.path("kind"));
            ObjectNode compact;
            if ("persisted_agent_task_projection".equals(kind)) {
                compact = compactStoredTaskProjection(projection, updatedAt, value.length());
            } else if ("persisted_agent_artifact_projection".equals(kind)) {
                compact = compactStoredArtifactProjection(projection, updatedAt, value.length());
            } else {
                compact = tinyStoredProjection(projection, updatedAt, kind, value.length());
            }
            String rendered = objectMapper.writeValueAsString(compact);
            if (rendered.length() <= budget || budget < 500) {
                return Optional.of(rendered);
            }
            return Optional.of(objectMapper.writeValueAsString(
                    tinyStoredProjection(projection, updatedAt, kind, value.length())));
        } catch (Exception e) {
            log.warn("Compact stored Agent projection failed: chars={}, error={}", value.length(), e.getMessage());
            return Optional.empty();
        }
    }

    private ObjectNode compactStoredTaskProjection(JsonNode projection, OffsetDateTime updatedAt, int originalChars) {
        ObjectNode out = storedProjectionBase(
                projection,
                updatedAt,
                "persisted_agent_task_projection",
                "compact",
                originalChars);
        copyField(out, projection, "taskProgress");
        ObjectNode task = out.putObject("taskState");
        JsonNode taskState = projection.path("taskState");
        task.set("todos", tailArray(taskState.path("todos"), 6, true));
        task.set("facts", tailArray(taskState.path("facts"), 6, true));
        task.set("decisions", tailArray(taskState.path("decisions"), 4, true));
        copyField(out, projection, "budget");
        return out;
    }

    private ObjectNode compactStoredArtifactProjection(JsonNode projection, OffsetDateTime updatedAt, int originalChars) {
        ObjectNode out = storedProjectionBase(
                projection,
                updatedAt,
                "persisted_agent_artifact_projection",
                "compact",
                originalChars);
        copyField(out, projection, "activeDocument");
        out.set("latestDocumentRevisions", tailArray(projection.path("latestDocumentRevisions"), 8, true));
        out.set("activeViews", tailArray(projection.path("activeViews"), 8, true));
        copyField(out, projection, "viewStatusSummary");
        out.set("readReuseHints", tailArray(projection.path("readReuseHints"), 4, true));
        return out;
    }

    private ObjectNode tinyStoredProjection(JsonNode projection,
                                            OffsetDateTime updatedAt,
                                            String fallbackKind,
                                            int originalChars) {
        String kind = firstNonBlank(fallbackKind, "persisted_agent_projection");
        ObjectNode out = storedProjectionBase(projection, updatedAt, kind, "tiny", originalChars);
        if ("persisted_agent_task_projection".equals(kind)) {
            copyField(out, projection, "taskProgress");
        } else if ("persisted_agent_artifact_projection".equals(kind)) {
            copyField(out, projection, "activeDocument");
            out.set("latestDocumentRevisions", tailArray(projection.path("latestDocumentRevisions"), 2, true));
            copyField(out, projection, "viewStatusSummary");
        }
        return out;
    }

    private ObjectNode storedProjectionBase(JsonNode projection,
                                            OffsetDateTime updatedAt,
                                            String fallbackKind,
                                            String level,
                                            int originalChars) {
        ObjectNode out = objectMapper.createObjectNode();
        putText(out, "kind", firstNonBlank(text(projection.path("kind")), fallbackKind));
        if (projection.path("schemaVersion").canConvertToInt()) {
            out.put("schemaVersion", projection.path("schemaVersion").asInt());
        } else {
            out.put("schemaVersion", 1);
        }
        out.put("projection_level", level);
        out.put("compacted", true);
        out.put("original_projection_chars", originalChars);
        copyField(out, projection, "source");
        if (updatedAt != null) {
            ObjectNode source = out.path("source").isObject()
                    ? (ObjectNode) out.path("source")
                    : out.putObject("source");
            source.put("loadedAt", updatedAt.toString());
        }
        return out;
    }

    Optional<String> compactLedgerSnapshotJson(String raw, OffsetDateTime updatedAt, int maxChars) {
        int budget = Math.max(maxChars, 0);
        if (budget <= 0) {
            return Optional.empty();
        }
        try {
            JsonNode snapshot = objectMapper.readTree(Objects.toString(raw, ""));
            ObjectNode projection = buildLedgerProjection(snapshot, updatedAt, false);
            String rendered = objectMapper.writeValueAsString(projection);
            if (rendered.length() <= budget) {
                return Optional.of(rendered);
            }

            ObjectNode compact = buildLedgerProjection(snapshot, updatedAt, true);
            compact.put("compacted", true);
            compact.put("original_projection_chars", rendered.length());
            rendered = objectMapper.writeValueAsString(compact);
            if (rendered.length() <= budget || budget < 500) {
                return Optional.of(rendered);
            }

            ObjectNode minimal = objectMapper.createObjectNode();
            minimal.put("kind", "persisted_agent_work_memory_projection");
            minimal.put("schemaVersion", 1);
            minimal.put("compacted", true);
            minimal.put("projection_level", "minimal");
            ObjectNode source = minimal.putObject("source");
            putText(source, "agentRunId", snapshot.path("agentRunId"));
            putText(source, "updatedAt", updatedAt == null ? null : updatedAt.toString());
            putText(minimal, "goal", snapshot.path("goal"));
            putText(minimal, "status", snapshot.path("status"));
            ObjectNode activeDocument = activeDocument(snapshot);
            if (!activeDocument.isEmpty()) {
                minimal.set("activeDocument", activeDocument);
            }
            minimal.set("taskProgress", taskProgress(snapshot, true));
            ObjectNode artifactState = minimal.putObject("artifactState");
            artifactState.set("latestDocumentRevisions", latestDocumentRevisions(snapshot, 4));
            ObjectNode taskState = minimal.putObject("taskState");
            taskState.set("todos", tailArray(snapshot.path("todos"), 3, true));
            rendered = objectMapper.writeValueAsString(minimal);
            if (rendered.length() <= budget) {
                return Optional.of(rendered);
            }
            return Optional.of(objectMapper.writeValueAsString(tinyLedgerProjection(snapshot, updatedAt)));
        } catch (Exception e) {
            log.warn("Build compact Agent ledger projection failed: chars={}, error={}",
                    raw == null ? 0 : raw.length(), e.getMessage());
            return Optional.empty();
        }
    }

    Optional<String> compactTaskProjectionJson(String raw, OffsetDateTime updatedAt, int maxChars) {
        int budget = Math.max(maxChars, 0);
        if (budget <= 0) {
            return Optional.empty();
        }
        try {
            JsonNode snapshot = objectMapper.readTree(Objects.toString(raw, ""));
            ObjectNode normal = buildTaskProjection(snapshot, updatedAt, false);
            String rendered = objectMapper.writeValueAsString(normal);
            if (rendered.length() <= budget) {
                return Optional.of(rendered);
            }

            ObjectNode compact = buildTaskProjection(snapshot, updatedAt, true);
            compact.put("compacted", true);
            compact.put("original_projection_chars", rendered.length());
            rendered = objectMapper.writeValueAsString(compact);
            if (rendered.length() <= budget || budget < 500) {
                return Optional.of(rendered);
            }
            return Optional.of(objectMapper.writeValueAsString(tinyTaskProjection(snapshot, updatedAt)));
        } catch (Exception e) {
            log.warn("Build compact Agent task projection failed: chars={}, error={}",
                    raw == null ? 0 : raw.length(), e.getMessage());
            return Optional.empty();
        }
    }

    Optional<String> compactArtifactProjectionJson(String raw, OffsetDateTime updatedAt, int maxChars) {
        int budget = Math.max(maxChars, 0);
        if (budget <= 0) {
            return Optional.empty();
        }
        try {
            JsonNode snapshot = objectMapper.readTree(Objects.toString(raw, ""));
            ObjectNode normal = buildArtifactProjection(snapshot, updatedAt, false);
            String rendered = objectMapper.writeValueAsString(normal);
            if (rendered.length() <= budget) {
                return Optional.of(rendered);
            }

            ObjectNode compact = buildArtifactProjection(snapshot, updatedAt, true);
            compact.put("compacted", true);
            compact.put("original_projection_chars", rendered.length());
            rendered = objectMapper.writeValueAsString(compact);
            if (rendered.length() <= budget || budget < 500) {
                return Optional.of(rendered);
            }
            return Optional.of(objectMapper.writeValueAsString(tinyArtifactProjection(snapshot, updatedAt)));
        } catch (Exception e) {
            log.warn("Build compact Agent artifact projection failed: chars={}, error={}",
                    raw == null ? 0 : raw.length(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<LedgerSnapshotRecord> findLatestLedgerSnapshot(long userId,
                                                                   long sessionId,
                                                                   Long projectId,
                                                                   OffsetDateTime minUpdatedAt) {
        if (userId <= 0 || sessionId <= 0) {
            return Optional.empty();
        }
        var condition = ARLS_USER_ID.eq(userId)
                .and(ARLS_SESSION_ID.eq(sessionId));
        Long pid = nullablePositive(projectId);
        if (pid != null) {
            condition = condition.and(ARLS_PROJECT_ID.eq(pid));
        }
        if (minUpdatedAt != null) {
            condition = condition.and(ARLS_UPDATED_AT.ge(minUpdatedAt));
        }

        var record = dsl.select(ARLS_LEDGER_JSON, ARLS_UPDATED_AT)
                .from(AGENT_RUN_LEDGER_SNAPSHOTS)
                .where(condition)
                .orderBy(ARLS_UPDATED_AT.desc())
                .limit(1)
                .fetchOne();
        if (record == null) {
            return Optional.empty();
        }
        JSONB json = record.get(ARLS_LEDGER_JSON);
        if (json == null || json.data() == null || json.data().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new LedgerSnapshotRecord(json.data(), record.get(ARLS_UPDATED_AT)));
    }

    private JSONB toJsonbOrNull(String raw) {
        String value = Objects.toString(raw, "").trim();
        if (value.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(value);
            return org.jooq.JSONB.valueOf(value);
        } catch (Exception e) {
            log.warn("Ignore invalid JSONB payload: chars={}, error={}", value.length(), e.getMessage());
            return null;
        }
    }

    private JSONB toJsonbOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        try {
            return org.jooq.JSONB.valueOf(objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            log.warn("Ignore invalid JSON node for JSONB: error={}", e.getMessage());
            return null;
        }
    }

    private void copyField(ObjectNode target, JsonNode source, String field) {
        if (target == null || source == null || field == null) {
            return;
        }
        JsonNode value = source.get(field);
        if (value != null && !value.isNull() && !value.isMissingNode()) {
            target.set(field, value.deepCopy());
        }
    }

    private ObjectNode buildLedgerProjection(JsonNode snapshot, OffsetDateTime updatedAt, boolean compact) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("kind", "persisted_agent_work_memory_projection");
        root.put("schemaVersion", 1);
        root.put("projection_level", compact ? "compact" : "normal");
        ObjectNode source = root.putObject("source");
        putText(source, "agentRunId", snapshot.path("agentRunId"));
        putText(source, "createdAt", snapshot.path("createdAt"));
        putText(source, "updatedAt", updatedAt == null ? null : updatedAt.toString());
        putText(root, "goal", snapshot.path("goal"), compact ? 500 : 1_000);
        putText(root, "status", snapshot.path("status"));
        ObjectNode activeDocument = activeDocument(snapshot);
        if (!activeDocument.isEmpty()) {
            root.set("activeDocument", activeDocument);
        }
        root.set("taskProgress", taskProgress(snapshot, compact));

        ObjectNode task = root.putObject("taskState");
        task.set("todos", tailArray(snapshot.path("todos"), compact ? 6 : 10, compact));
        task.set("facts", tailArray(snapshot.path("facts"), compact ? 6 : 10, compact));
        task.set("decisions", tailArray(snapshot.path("decisions"), compact ? 4 : 6, compact));

        ObjectNode artifactState = root.putObject("artifactState");
        if (!activeDocument.isEmpty()) {
            artifactState.set("activeDocument", activeDocument.deepCopy());
        }
        artifactState.set("artifacts", tailArray(snapshot.path("artifacts"), compact ? 6 : 10, compact));
        artifactState.set("latestDocumentRevisions", latestDocumentRevisions(snapshot, compact ? 6 : 10));
        ArrayNode activeViews = activeViews(snapshot, compact ? 5 : 8, compact);
        artifactState.set("activeViews", activeViews);
        artifactState.set("viewStatusSummary", viewStatusSummary(activeViews));

        root.set("recentToolCalls", tailArray(snapshot.path("recentToolCalls"), compact ? 5 : 8, compact));
        root.set("readReuseHints", tailArray(snapshot.path("reuseHints"), compact ? 4 : 6, compact));
        root.set("budget", snapshot.path("budget").isObject() ? snapshot.path("budget") : objectMapper.createObjectNode());
        ArrayNode rules = root.putArray("rules");
        rules.add("This is backend-persisted compact state from the previous Agent run for the same session/project.");
        rules.add("Use it to continue task progress and artifact identity without replaying old tool results.");
        rules.add("Before writing, verify artifact revision with a narrow read if it may have changed.");
        return root;
    }

    private ObjectNode buildTaskProjection(JsonNode snapshot, OffsetDateTime updatedAt, boolean compact) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("kind", "persisted_agent_task_projection");
        root.put("schemaVersion", 1);
        root.put("projection_level", compact ? "compact" : "normal");
        ObjectNode source = root.putObject("source");
        putText(source, "agentRunId", snapshot.path("agentRunId"));
        putText(source, "createdAt", snapshot.path("createdAt"));
        putText(source, "updatedAt", updatedAt == null ? null : updatedAt.toString());
        root.set("taskProgress", taskProgress(snapshot, compact));
        ObjectNode task = root.putObject("taskState");
        task.set("todos", tailArray(snapshot.path("todos"), compact ? 6 : 10, compact));
        task.set("facts", tailArray(snapshot.path("facts"), compact ? 6 : 10, compact));
        task.set("decisions", tailArray(snapshot.path("decisions"), compact ? 4 : 6, compact));
        root.set("budget", snapshot.path("budget").isObject() ? snapshot.path("budget") : objectMapper.createObjectNode());
        ArrayNode rules = root.putArray("rules");
        rules.add("This is previous Agent task state for the same session/project.");
        rules.add("Use it to continue task progress without replaying old chat/tool history.");
        rules.add("Treat page progress as heuristic unless confirmed by a current read or successful write.");
        return root;
    }

    private ObjectNode buildArtifactProjection(JsonNode snapshot, OffsetDateTime updatedAt, boolean compact) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("kind", "persisted_agent_artifact_projection");
        root.put("schemaVersion", 1);
        root.put("projection_level", compact ? "compact" : "normal");
        ObjectNode source = root.putObject("source");
        putText(source, "agentRunId", snapshot.path("agentRunId"));
        putText(source, "createdAt", snapshot.path("createdAt"));
        putText(source, "updatedAt", updatedAt == null ? null : updatedAt.toString());
        ObjectNode activeDocument = activeDocument(snapshot);
        if (!activeDocument.isEmpty()) {
            root.set("activeDocument", activeDocument);
        }
        root.set("latestDocumentRevisions", latestDocumentRevisions(snapshot, compact ? 6 : 10));
        ArrayNode activeViews = activeViews(snapshot, compact ? 5 : 8, compact);
        root.set("activeViews", activeViews);
        root.set("viewStatusSummary", viewStatusSummary(activeViews));
        root.set("readReuseHints", tailArray(snapshot.path("reuseHints"), compact ? 4 : 6, compact));
        ArrayNode rules = root.putArray("rules");
        rules.add("This is latest known artifact state from previous Agent work for the same session/project.");
        rules.add("Use activeDocument/latestDocumentRevisions to avoid rediscovering document identity.");
        rules.add("Use views with status=latest for orientation; reopen or narrow-read stale/unknown views before writing.");
        return root;
    }

    private ObjectNode tinyLedgerProjection(JsonNode snapshot, OffsetDateTime updatedAt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("kind", "persisted_agent_work_memory_projection");
        root.put("schemaVersion", 1);
        root.put("compacted", true);
        root.put("projection_level", "tiny");
        ObjectNode source = root.putObject("source");
        putText(source, "agentRunId", snapshot.path("agentRunId"));
        putText(source, "updatedAt", updatedAt == null ? null : updatedAt.toString());
        putText(root, "goal", snapshot.path("goal"), 200);
        putText(root, "status", snapshot.path("status"));
        ObjectNode activeDocument = activeDocument(snapshot);
        if (!activeDocument.isEmpty()) {
            root.set("activeDocument", activeDocument);
        }
        ObjectNode progress = root.putObject("taskProgress");
        Integer nextPage = inferNextPageFromSnapshot(snapshot);
        if (nextPage != null) {
            progress.put("candidateNextPage", nextPage);
            progress.put("pageProgressConfidence", "heuristic");
        }
        ObjectNode artifactState = root.putObject("artifactState");
        artifactState.set("latestDocumentRevisions", latestDocumentRevisions(snapshot, 2));
        return root;
    }

    private ObjectNode tinyTaskProjection(JsonNode snapshot, OffsetDateTime updatedAt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("kind", "persisted_agent_task_projection");
        root.put("schemaVersion", 1);
        root.put("compacted", true);
        root.put("projection_level", "tiny");
        ObjectNode source = root.putObject("source");
        putText(source, "agentRunId", snapshot.path("agentRunId"));
        putText(source, "updatedAt", updatedAt == null ? null : updatedAt.toString());
        putText(root, "goal", snapshot.path("goal"), 200);
        putText(root, "status", snapshot.path("status"));
        ObjectNode progress = root.putObject("taskProgress");
        Integer nextPage = inferNextPageFromSnapshot(snapshot);
        if (nextPage != null) {
            progress.put("candidateNextPage", nextPage);
            progress.put("pageProgressConfidence", "heuristic");
        }
        return root;
    }

    private ObjectNode tinyArtifactProjection(JsonNode snapshot, OffsetDateTime updatedAt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("kind", "persisted_agent_artifact_projection");
        root.put("schemaVersion", 1);
        root.put("compacted", true);
        root.put("projection_level", "tiny");
        ObjectNode source = root.putObject("source");
        putText(source, "agentRunId", snapshot.path("agentRunId"));
        putText(source, "updatedAt", updatedAt == null ? null : updatedAt.toString());
        ObjectNode activeDocument = activeDocument(snapshot);
        if (!activeDocument.isEmpty()) {
            root.set("activeDocument", activeDocument);
        }
        root.set("latestDocumentRevisions", latestDocumentRevisions(snapshot, 2));
        return root;
    }

    private ObjectNode activeDocument(JsonNode snapshot) {
        ArrayNode latest = latestDocumentRevisions(snapshot, 1);
        ObjectNode out = objectMapper.createObjectNode();
        if (latest.size() == 0 || !latest.get(0).isObject()) {
            return out;
        }
        latest.get(0).fields().forEachRemaining(entry -> out.set(entry.getKey(), entry.getValue()));
        return out;
    }

    private Integer inferNextPageFromSnapshot(JsonNode snapshot) {
        List<JsonNode> activeTodos = new ArrayList<>();
        Set<Integer> completedPages = new TreeSet<>();
        JsonNode todos = snapshot.path("todos");
        if (todos.isArray()) {
            for (JsonNode todo : todos) {
                String status = text(todo.path("status")).toLowerCase();
                if ("done".equals(status) || "completed".equals(status)) {
                    collectPageNumbers(completedPages, todo.path("text").asText(""));
                    collectPageNumbers(completedPages, todo.path("note").asText(""));
                } else if ("pending".equals(status) || "in_progress".equals(status) || "blocked".equals(status) || status.isBlank()) {
                    activeTodos.add(todo);
                }
            }
        }
        return inferNextPage(activeTodos, completedPages);
    }

    private ObjectNode taskProgress(JsonNode snapshot, boolean compact) {
        ObjectNode out = objectMapper.createObjectNode();
        putText(out, "goal", snapshot.path("goal"), compact ? 500 : 1_000);
        putText(out, "status", snapshot.path("status"));

        List<JsonNode> activeTodos = new ArrayList<>();
        List<JsonNode> completedTodos = new ArrayList<>();
        List<JsonNode> blockedTodos = new ArrayList<>();
        JsonNode todos = snapshot.path("todos");
        if (todos.isArray()) {
            for (JsonNode todo : todos) {
                String status = text(todo.path("status")).toLowerCase();
                if ("done".equals(status) || "completed".equals(status)) {
                    completedTodos.add(todo);
                } else if ("blocked".equals(status)) {
                    blockedTodos.add(todo);
                    activeTodos.add(todo);
                } else if ("pending".equals(status) || "in_progress".equals(status) || status.isBlank()) {
                    activeTodos.add(todo);
                }
            }
        }

        out.set("activeTodos", nodeTail(activeTodos, compact ? 4 : 6, compact));
        out.set("completedTodos", nodeTail(completedTodos, compact ? 3 : 5, compact));
        out.set("blockedTodos", nodeTail(blockedTodos, compact ? 2 : 4, compact));
        if (!activeTodos.isEmpty()) {
            out.set("nextTodo", compactNode(activeTodos.getFirst(), compact));
        }

        Set<Integer> completedPages = new TreeSet<>();
        for (JsonNode todo : completedTodos) {
            collectPageNumbers(completedPages, todo.path("text").asText(""));
            collectPageNumbers(completedPages, todo.path("note").asText(""));
        }
        JsonNode facts = snapshot.path("facts");
        if (facts.isArray()) {
            for (JsonNode fact : facts) {
                collectPageNumbers(completedPages, fact.path("text").asText(""));
            }
        }
        ArrayNode pages = out.putArray("candidateCompletedPages");
        completedPages.forEach(pages::add);
        Integer nextPage = inferNextPage(activeTodos, completedPages);
        if (nextPage != null) {
            out.put("candidateNextPage", nextPage);
            out.put("pageProgressConfidence", "heuristic");
        }
        return out;
    }

    private ArrayNode activeViews(JsonNode snapshot, int maxItems, boolean compact) {
        List<ObjectNode> views = new ArrayList<>();
        JsonNode calls = snapshot.path("recentToolCalls");
        if (!calls.isArray()) {
            return objectMapper.createArrayNode();
        }
        Map<Long, String> latestRevisions = latestRevisionByDocument(snapshot);
        for (JsonNode call : calls) {
            String tool = text(call.path("tool"));
            if (!isReadViewTool(tool) || !"success".equalsIgnoreCase(text(call.path("status")))) {
                continue;
            }
            ObjectNode view = objectMapper.createObjectNode();
            putText(view, "viewId", firstNonBlank(text(call.path("readKey")), text(call.path("callId"))), 800);
            putText(view, "callId", call.path("callId"));
            putText(view, "tool", tool);
            putText(view, "viewType", viewType(tool));
            putText(view, "summary", call.path("summary"), compact ? 300 : 800);
            putText(view, "revision", call.path("revision"));
            if (call.path("documentId").canConvertToLong()) {
                view.put("documentId", call.path("documentId").asLong());
            }
            if (call.path("fileId").canConvertToLong()) {
                view.put("fileId", call.path("fileId").asLong());
            }
            if (call.path("resultChars").canConvertToInt()) {
                view.put("resultChars", call.path("resultChars").asInt());
            }
            applyReadKeyParts(view, text(call.path("readKey")));
            applyViewFreshness(view, latestRevisions);
            views.add(view);
        }
        return nodeTail(views, maxItems);
    }

    private ObjectNode viewStatusSummary(ArrayNode views) {
        ObjectNode out = objectMapper.createObjectNode();
        int total = 0;
        int latest = 0;
        int stale = 0;
        int unknown = 0;
        for (JsonNode view : views) {
            total++;
            String status = text(view.path("status"));
            if ("latest".equals(status)) {
                latest++;
            } else if ("stale".equals(status)) {
                stale++;
            } else {
                unknown++;
            }
        }
        out.put("total", total);
        out.put("latest", latest);
        out.put("stale", stale);
        out.put("unknown", unknown);
        return out;
    }

    private ArrayNode nodeTail(List<? extends JsonNode> nodes, int maxItems) {
        ArrayNode out = objectMapper.createArrayNode();
        int limit = Math.max(maxItems, 1);
        int start = Math.max(0, nodes.size() - limit);
        for (int i = start; i < nodes.size(); i++) {
            out.add(nodes.get(i));
        }
        return out;
    }

    private ArrayNode nodeTail(List<JsonNode> nodes, int maxItems, boolean compact) {
        ArrayNode out = objectMapper.createArrayNode();
        int limit = Math.max(maxItems, 1);
        int start = Math.max(0, nodes.size() - limit);
        for (int i = start; i < nodes.size(); i++) {
            out.add(compactNode(nodes.get(i), compact));
        }
        return out;
    }

    private ArrayNode latestDocumentRevisions(JsonNode snapshot, int maxItems) {
        Map<String, ObjectNode> byDocument = new LinkedHashMap<>();
        collectDocumentRevision(byDocument, snapshot.path("artifacts"), "artifact");
        collectDocumentRevision(byDocument, snapshot.path("recentToolCalls"), "tool_call");

        ArrayNode out = objectMapper.createArrayNode();
        int skip = Math.max(byDocument.size() - Math.max(maxItems, 1), 0);
        int index = 0;
        for (ObjectNode value : byDocument.values()) {
            if (index++ < skip) {
                continue;
            }
            out.add(value);
        }
        return out;
    }

    private void collectDocumentRevision(Map<String, ObjectNode> byDocument, JsonNode entries, String sourceKind) {
        if (!entries.isArray()) {
            return;
        }
        for (JsonNode entry : entries) {
            JsonNode documentId = entry.path("documentId");
            if (!documentId.canConvertToLong()) {
                continue;
            }
            ObjectNode node = objectMapper.createObjectNode();
            node.put("documentId", documentId.asLong());
            putText(node, "revision", entry.path("revision"));
            putText(node, "path", entry.path("path"));
            putText(node, "sourceCallId", entry.path("sourceCallId"));
            putText(node, "callId", entry.path("callId"));
            putText(node, "sourceKind", sourceKind);
            byDocument.put(String.valueOf(documentId.asLong()), node);
        }
    }

    private Map<Long, String> latestRevisionByDocument(JsonNode snapshot) {
        Map<String, ObjectNode> byDocument = new LinkedHashMap<>();
        collectDocumentRevision(byDocument, snapshot.path("artifacts"), "artifact");
        collectDocumentRevision(byDocument, snapshot.path("recentToolCalls"), "tool_call");
        Map<Long, String> out = new LinkedHashMap<>();
        for (ObjectNode node : byDocument.values()) {
            if (!node.path("documentId").canConvertToLong()) {
                continue;
            }
            String revision = text(node.path("revision"));
            if (!revision.isBlank()) {
                out.put(node.path("documentId").asLong(), revision);
            }
        }
        return out;
    }

    private Integer inferNextPage(List<JsonNode> activeTodos, Set<Integer> completedPages) {
        for (JsonNode todo : activeTodos) {
            Set<Integer> pages = new TreeSet<>();
            collectPageNumbers(pages, todo.path("text").asText(""));
            collectPageNumbers(pages, todo.path("note").asText(""));
            if (!pages.isEmpty()) {
                return pages.iterator().next();
            }
        }
        if (!completedPages.isEmpty()) {
            return completedPages.stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        }
        return null;
    }

    private static void collectPageNumbers(Set<Integer> out, String text) {
        String value = Objects.toString(text, "");
        collectPageRanges(out, value);
        collectPageMatches(out, CN_PAGE_PATTERN.matcher(value));
        collectPageMatches(out, EN_PAGE_PATTERN.matcher(value));
    }

    private static void collectPageRanges(Set<Integer> out, String text) {
        Matcher matcher = CN_PAGE_RANGE_PATTERN.matcher(Objects.toString(text, ""));
        while (matcher.find()) {
            int start = safePage(matcher.group(1));
            int end = safePage(matcher.group(2));
            if (start <= 0 || end <= 0) {
                continue;
            }
            int low = Math.min(start, end);
            int high = Math.max(start, end);
            if (high - low > 100) {
                continue;
            }
            for (int i = low; i <= high; i++) {
                out.add(i);
            }
        }
    }

    private static void collectPageMatches(Set<Integer> out, Matcher matcher) {
        while (matcher.find()) {
            int page = safePage(matcher.group(1));
            if (page > 0) {
                out.add(page);
            }
        }
    }

    private static int safePage(String raw) {
        try {
            int value = Integer.parseInt(Objects.toString(raw, "").trim());
            return value > 0 && value <= 999 ? value : -1;
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static Long parsePositiveLong(String raw) {
        try {
            long value = Long.parseLong(Objects.toString(raw, "").trim());
            return value > 0 ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Long positiveLong(JsonNode node) {
        if (node != null && node.canConvertToLong()) {
            long value = node.asLong();
            return value > 0 ? value : null;
        }
        return null;
    }

    Optional<ArtifactWriteImpact> extractArtifactWriteImpact(ToolCallEntry entry) {
        if (entry == null || !isSuccessfulToolCall(entry) || !isWriteArtifactTool(entry.getToolName())) {
            return Optional.empty();
        }
        JsonNode args = parseJsonNode(entry.getArgumentsJson()).orElse(objectMapper.createObjectNode());
        JsonNode result = parseJsonNode(entry.getResultJson()).orElse(objectMapper.createObjectNode());
        if (result.path("success").isBoolean() && !result.path("success").asBoolean(true)) {
            return Optional.empty();
        }

        Long documentId = firstPositiveLong(
                positiveLong(result.path("document_id")),
                positiveLong(result.path("documentId")),
                positiveLong(args.path("document_id")),
                positiveLong(args.path("documentId"))
        );
        if (documentId == null) {
            return Optional.empty();
        }

        Long fileId = firstPositiveLong(
                positiveLong(result.path("file_id")),
                positiveLong(result.path("fileId")),
                positiveLong(args.path("file_id")),
                positiveLong(args.path("fileId"))
        );
        String revision = firstNonBlank(
                firstNonBlank(text(result.path("revision")), text(result.path("new_revision"))),
                firstNonBlank(text(result.path("newRevision")), text(result.path("current_revision")))
        );

        LinkedHashSet<String> paths = new LinkedHashSet<>();
        collectOperationPaths(args.path("operations"), paths);
        collectOperationPaths(args.path("patches"), paths);
        collectOperationPaths(args.path("ops"), paths);
        collectOperationPaths(parseOperationsJson(args.path("operations_json")).orElse(null), paths);
        collectOperationPaths(parseOperationsJson(args.path("patches_json")).orElse(null), paths);
        collectOperationPaths(result.path("operations"), paths);
        collectTextArrayPaths(result.path("changed_paths"), paths);
        collectTextArrayPaths(result.path("modified_paths"), paths);
        collectTextArrayPaths(result.path("paths"), paths);
        if (paths.isEmpty()) {
            addJsonPointer(paths, "/");
        }

        return Optional.of(new ArtifactWriteImpact(
                documentId,
                fileId,
                revision,
                List.copyOf(paths).stream().limit(MAX_WRITE_IMPACT_PATHS).toList(),
                defaultText(entry.getToolName(), "unknown"),
                defaultText(entry.getCallId(), "")
        ));
    }

    private Optional<JsonNode> parseOperationsJson(JsonNode node) {
        String raw = text(node);
        if (raw.isBlank()) {
            return Optional.empty();
        }
        return parseJsonNode(raw);
    }

    private void collectOperationPaths(JsonNode operations, LinkedHashSet<String> out) {
        if (operations == null || !operations.isArray()) {
            return;
        }
        for (JsonNode operation : operations) {
            if (!operation.isObject()) {
                continue;
            }
            addJsonPointer(out, text(operation.path("path")));
            addJsonPointer(out, text(operation.path("from")));
        }
    }

    private void collectTextArrayPaths(JsonNode paths, LinkedHashSet<String> out) {
        if (!paths.isArray()) {
            return;
        }
        for (JsonNode path : paths) {
            if (path.isTextual()) {
                addJsonPointer(out, path.asText());
            }
        }
    }

    private void addJsonPointer(LinkedHashSet<String> out, String raw) {
        String path = normalizeJsonPointer(raw);
        if (!path.isBlank()) {
            out.add(path);
        }
    }

    private static boolean isSuccessfulToolCall(ToolCallEntry entry) {
        if (entry == null) {
            return false;
        }
        if (Boolean.FALSE.equals(entry.getSuccess())) {
            return false;
        }
        String status = Objects.toString(entry.getStatus(), "").trim();
        return status.isBlank() || "success".equalsIgnoreCase(status);
    }

    private static boolean isWriteArtifactTool(String tool) {
        return switch (Objects.toString(tool, "")) {
            case "document_save" -> true;
            default -> false;
        };
    }

    private static Long firstPositiveLong(Long... values) {
        if (values == null) {
            return null;
        }
        for (Long value : values) {
            if (value != null && value > 0) {
                return value;
            }
        }
        return null;
    }

    private static boolean viewTouchedByWrite(JsonNode view, List<String> modifiedPaths) {
        if (view == null || !view.isObject()) {
            return true;
        }
        if (modifiedPaths == null || modifiedPaths.isEmpty()) {
            return true;
        }
        String viewPath = text(view.path("path"));
        if (!viewPath.isBlank()) {
            for (String modifiedPath : modifiedPaths) {
                if (jsonPointerMayOverlap(viewPath, modifiedPath)) {
                    return true;
                }
            }
            return false;
        }

        return true;
    }

    static boolean jsonPointerMayOverlap(String left, String right) {
        String a = normalizeJsonPointer(left);
        String b = normalizeJsonPointer(right);
        if (a.isBlank() || b.isBlank() || "/".equals(a) || "/".equals(b)) {
            return true;
        }
        return a.equals(b) || a.startsWith(b + "/") || b.startsWith(a + "/");
    }

    private static String projectionScopeKey(Long projectId) {
        Long pid = nullablePositive(projectId);
        return pid == null ? "none" : String.valueOf(pid);
    }

    private static boolean isReadViewTool(String tool) {
        return switch (Objects.toString(tool, "")) {
            case "json_structure_read",
                    "json_subtree_read",
                    "document_read",
                    "file_content_read" -> true;
            default -> false;
        };
    }

    private static String viewType(String tool) {
        return switch (Objects.toString(tool, "")) {
            case "json_structure_read" -> "json_structure";
            case "json_subtree_read" -> "json_subtree";
            case "document_read" -> "document_text";
            case "file_content_read" -> "file_content";
            default -> "tool_view";
        };
    }

    private void applyReadKeyParts(ObjectNode view, String readKey) {
        String key = Objects.toString(readKey, "");
        if (key.isBlank()) {
            return;
        }
        String[] parts = key.split("\\|", -1);
        if (parts.length > 0 && !parts[0].isBlank()) {
            putText(view, "readTool", parts[0]);
        }
        for (String part : parts) {
            int idx = part.indexOf('=');
            if (idx <= 0) {
                if (part.startsWith("document:") && !view.path("documentId").canConvertToLong()) {
                    Long documentId = parsePositiveLong(part.substring("document:".length()));
                    if (documentId != null) {
                        view.put("documentId", documentId);
                    }
                }
                continue;
            }
            String name = part.substring(0, idx);
            String value = part.substring(idx + 1);
            switch (name) {
                case "rev" -> putText(view, "readKeyRevision", value);
                case "path" -> putText(view, "path", value, 800);
                case "window" -> putText(view, "window", value, 800);
                default -> {
                }
            }
        }
    }

    private void applyViewFreshness(ObjectNode view, Map<Long, String> latestRevisions) {
        Long documentId = view.path("documentId").canConvertToLong() ? view.path("documentId").asLong() : null;
        if (documentId == null) {
            view.put("status", "unknown");
            view.put("statusReason", "missing_document_id");
            return;
        }
        String latestRevision = latestRevisions.get(documentId);
        if (latestRevision == null || latestRevision.isBlank()) {
            view.put("status", "unknown");
            view.put("statusReason", "missing_latest_document_revision");
            return;
        }
        putText(view, "latestDocumentRevision", latestRevision);
        String viewRevision = firstNonBlank(text(view.path("revision")), text(view.path("readKeyRevision")));
        if (viewRevision.isBlank()) {
            view.put("status", "unknown");
            view.put("statusReason", "missing_view_revision");
            return;
        }
        if (latestRevision.equals(viewRevision)) {
            view.put("status", "latest");
        } else {
            view.put("status", "stale");
            view.put("statusReason", "document_revision_changed_after_view_read");
        }
    }

    private ArrayNode tailArray(JsonNode source, int maxItems, boolean compactText) {
        ArrayNode out = objectMapper.createArrayNode();
        if (!source.isArray()) {
            return out;
        }
        int limit = Math.max(maxItems, 1);
        int start = Math.max(0, source.size() - limit);
        for (int i = start; i < source.size(); i++) {
            JsonNode item = source.get(i);
            out.add(compactNode(item, compactText));
        }
        return out;
    }

    private JsonNode compactNode(JsonNode item, boolean compactText) {
        if (!item.isObject()) {
            return item;
        }
        ObjectNode out = objectMapper.createObjectNode();
        item.fields().forEachRemaining(entry -> {
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                return;
            }
            if (value.isTextual()) {
                int limit = compactText ? 300 : 800;
                out.put(entry.getKey(), abbreviate(value.asText(), limit));
            } else if (value.isNumber() || value.isBoolean()) {
                out.set(entry.getKey(), value);
            } else if (!compactText) {
                out.set(entry.getKey(), value);
            }
        });
        return out;
    }

    private void putText(ObjectNode node, String field, JsonNode value) {
        putText(node, field, value, 500);
    }

    private void putText(ObjectNode node, String field, JsonNode value, int maxChars) {
        if (value != null && value.isTextual()) {
            putText(node, field, value.asText(), maxChars);
        }
    }

    private void putText(ObjectNode node, String field, String value) {
        putText(node, field, value, 500);
    }

    private void putText(ObjectNode node, String field, String value, int maxChars) {
        String text = Objects.toString(value, "").trim();
        if (!text.isBlank()) {
            node.put(field, abbreviate(text, maxChars));
        }
    }

    private static String abbreviate(String value, int maxChars) {
        String text = Objects.toString(value, "");
        int limit = Math.max(maxChars, 1);
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit) + "...(truncated,len=" + text.length() + ")";
    }

    private void putCappedJson(ObjectNode out, String field, JsonNode value, int maxChars) throws Exception {
        if (value == null || value.isMissingNode()) {
            return;
        }
        String rendered = objectMapper.writeValueAsString(value);
        out.put(field + "_chars", rendered.length());
        if (rendered.length() <= Math.max(maxChars, 1)) {
            out.put(field + "_truncated", false);
            out.set(field, value.deepCopy());
            return;
        }
        out.put(field + "_truncated", true);
        out.put(field + "_preview", abbreviate(rendered, maxChars));
    }

    private static String normalizeArtifactViewMode(String mode) {
        String value = Objects.toString(mode, "").trim().toLowerCase();
        return switch (value) {
            case "detail", "path" -> value;
            default -> "summary";
        };
    }

    private static String normalizeJsonPointer(String path) {
        String value = Objects.toString(path, "").trim();
        if (value.isBlank()) {
            return "";
        }
        return value.startsWith("/") ? value : "/" + value;
    }

    private static int clamp(int value, int min, int max, int fallback) {
        int candidate = value > 0 ? value : fallback;
        return Math.min(Math.max(candidate, min), max);
    }

    private static String text(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : "";
    }

    private static String firstNonBlank(String first, String second) {
        String a = Objects.toString(first, "").trim();
        return a.isBlank() ? Objects.toString(second, "").trim() : a;
    }

    private JSONB jsonbObjectOrEmpty(String raw) {
        Optional<JsonNode> parsed = parseJsonNode(raw);
        if (parsed.isPresent() && parsed.get().isObject()) {
            JSONB jsonb = toJsonbOrNull(parsed.get());
            if (jsonb != null) {
                return jsonb;
            }
        }
        return org.jooq.JSONB.valueOf("{}");
    }

    private JSONB jsonbObjectOrNull(String raw) {
        Optional<JsonNode> parsed = parseJsonNode(raw);
        if (parsed.isPresent() && parsed.get().isObject()) {
            return toJsonbOrNull(parsed.get());
        }
        return null;
    }

    private JSONB jsonbArrayFromStrings(Iterable<String> values) {
        ArrayNode out = objectMapper.createArrayNode();
        if (values != null) {
            for (String value : values) {
                String normalized = Objects.toString(value, "").trim();
                if (!normalized.isBlank()) {
                    out.add(normalized);
                }
            }
        }
        JSONB jsonb = toJsonbOrNull(out);
        return jsonb == null ? org.jooq.JSONB.valueOf("[]") : jsonb;
    }

    private JSONB jsonbArrayFromIntegers(Iterable<Integer> values) {
        ArrayNode out = objectMapper.createArrayNode();
        if (values != null) {
            for (Integer value : values) {
                if (value != null && value > 0 && value <= 999) {
                    out.add(value);
                }
            }
        }
        JSONB jsonb = toJsonbOrNull(out);
        return jsonb == null ? org.jooq.JSONB.valueOf("[]") : jsonb;
    }

    private JSONB jsonbArrayOrEmpty(JSONB jsonb) {
        if (jsonb == null || jsonb.data() == null || jsonb.data().isBlank()) {
            return org.jooq.JSONB.valueOf("[]");
        }
        Optional<JsonNode> parsed = parseJsonNode(jsonb.data());
        if (parsed.isPresent() && parsed.get().isArray()) {
            return jsonb;
        }
        return org.jooq.JSONB.valueOf("[]");
    }

    private LinkedHashSet<String> stringSetFromJsonb(JSONB jsonb) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (jsonb == null || jsonb.data() == null || jsonb.data().isBlank()) {
            return out;
        }
        parseJsonNode(jsonb.data()).ifPresent(node -> {
            if (!node.isArray()) {
                return;
            }
            for (JsonNode item : node) {
                String value = text(item);
                if (!value.isBlank()) {
                    out.add(value);
                }
            }
        });
        return out;
    }

    private JSONB documentRevisionMetadata(ToolCallEntry entry,
                                           ArtifactWriteImpact impact,
                                           RevisionValue beforeRevision,
                                           RevisionValue afterRevision) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("schemaVersion", 1);
        metadata.put("kind", "agent_run_document_revision_index");
        metadata.put("indexSource", "backend_tool_call_hook");
        putText(metadata, "agentRunId", entry.getRunId());
        putText(metadata, "callId", impact.callId());
        putText(metadata, "toolName", impact.toolName());
        metadata.put("documentId", impact.documentId());
        putText(metadata, "beforeRevisionSource", beforeRevision.source());
        putText(metadata, "afterRevisionSource", afterRevision.source());
        ArrayNode paths = metadata.putArray("latestModifiedPaths");
        impact.modifiedPaths().forEach(paths::add);
        JSONB jsonb = toJsonbOrNull(metadata);
        return jsonb == null ? org.jooq.JSONB.valueOf("{}") : jsonb;
    }

    private RevisionValue extractBeforeRevision(JsonNode args, JsonNode result) {
        return firstRevisionValue(
                revisionValue(result, "before_revision", "result.before_revision"),
                revisionValue(result, "beforeRevision", "result.beforeRevision"),
                revisionValue(result, "previous_revision", "result.previous_revision"),
                revisionValue(result, "previousRevision", "result.previousRevision"),
                revisionValue(args, "expected_revision", "arguments.expected_revision"),
                revisionValue(args, "expectedRevision", "arguments.expectedRevision"),
                revisionValue(args, "base_revision", "arguments.base_revision"),
                revisionValue(args, "baseRevision", "arguments.baseRevision")
        );
    }

    private RevisionValue extractAfterRevision(ArtifactWriteImpact impact, JsonNode result) {
        return firstRevisionValue(
                revisionValue(result, "after_revision", "result.after_revision"),
                revisionValue(result, "afterRevision", "result.afterRevision"),
                revisionValue(result, "revision", "result.revision"),
                revisionValue(result, "new_revision", "result.new_revision"),
                revisionValue(result, "newRevision", "result.newRevision"),
                revisionValue(result, "current_revision", "result.current_revision"),
                revisionValue(result, "currentRevision", "result.currentRevision"),
                revisionValue(impact.newRevision(), "artifact_write_impact.newRevision")
        );
    }

    private RevisionValue revisionValue(JsonNode source, String field, String revisionSource) {
        if (source == null || field == null) {
            return RevisionValue.empty();
        }
        return revisionValue(text(source.path(field)), revisionSource);
    }

    private RevisionValue revisionValue(String value, String source) {
        String revision = Objects.toString(value, "").trim();
        if (revision.isBlank()) {
            return RevisionValue.empty();
        }
        return new RevisionValue(revision, source);
    }

    private RevisionValue firstRevisionValue(RevisionValue... values) {
        if (values == null) {
            return RevisionValue.empty();
        }
        for (RevisionValue value : values) {
            if (value != null && !value.value().isBlank()) {
                return value;
            }
        }
        return RevisionValue.empty();
    }

    private static String extractDocumentType(JsonNode args, JsonNode result) {
        return firstNonBlank(
                firstNonBlank(text(result.path("document_type")), text(result.path("documentType"))),
                firstNonBlank(text(args.path("document_type")), text(args.path("documentType")))
        );
    }

    private static String extractCanonicalKind(JsonNode args, JsonNode result) {
        return firstNonBlank(
                firstNonBlank(text(result.path("canonical_kind")), text(result.path("canonicalKind"))),
                firstNonBlank(text(args.path("canonical_kind")), text(args.path("canonicalKind")))
        );
    }

    private static String inferCanonicalKind(String toolName) {
        String tool = Objects.toString(toolName, "").trim();
        if (tool.startsWith("document_")) {
            return "document";
        }
        return "";
    }

    private static String normalizeSessionAgentRunStatus(String status, String fallback) {
        String value = defaultText(status, fallback).trim().toLowerCase();
        return switch (value) {
            case "completed", "complete", "success", "succeeded" -> "completed";
            case "cancelled", "canceled" -> "cancelled";
            case "aborted", "abort" -> "aborted";
            case "failed", "failure", "error", "errored" -> "error";
            case "running", "in_progress", "started" -> "running";
            default -> normalizeSessionAgentRunStatus(fallback, "running");
        };
    }

    private record LedgerSnapshotRecord(String rawJson, OffsetDateTime updatedAt) {
    }

    private record ProjectionRecord(String rawJson, OffsetDateTime updatedAt) {
    }

    private record ArtifactViewRecord(String viewKey,
                                      Long documentId,
                                      Long fileId,
                                      String toolName,
                                      String viewType,
                                      String readKey,
                                      String revision,
                                      String status,
                                      Integer resultChars,
                                      String projectionJson,
                                      String sourceRunId,
                                      String sourceCallId,
                                      OffsetDateTime sourceUpdatedAt,
                                      OffsetDateTime updatedAt) {
    }

    private record ToolResultRecord(String toolName,
                                    String resultJson,
                                    Integer resultChars,
                                    Boolean compactedForModel,
                                    OffsetDateTime storedAt) {
    }

    private record RevisionValue(String value, String source) {
        RevisionValue {
            value = Objects.toString(value, "").trim();
            source = Objects.toString(source, "").trim();
        }

        static RevisionValue empty() {
            return new RevisionValue("", "");
        }
    }

    record ArtifactWriteImpact(Long documentId,
                               Long fileId,
                               String newRevision,
                               List<String> modifiedPaths,
                               String toolName,
                               String callId) {
        ArtifactWriteImpact {
            newRevision = Objects.toString(newRevision, "").trim();
            modifiedPaths = modifiedPaths == null ? List.of() : List.copyOf(modifiedPaths);
            toolName = Objects.toString(toolName, "").trim();
            callId = Objects.toString(callId, "").trim();
        }

        ArtifactWriteImpact withFileId(Long value) {
            return new ArtifactWriteImpact(documentId, value, newRevision, modifiedPaths, toolName, callId);
        }
    }

    public record SessionAgentRunMessageSpan(Long id,
                                             String agentRunId,
                                             String status,
                                             Integer firstMessageSeq,
                                             Integer lastMessageSeq) {
    }

    public record ActiveContextCompaction(Long id,
                                          String provider,
                                          String contextFormat,
                                          String compactedInputJson,
                                          Integer sourceLastSeq,
                                          String model,
                                          String responseId,
                                          OffsetDateTime createdAt) {
    }

    private static Long nullablePositive(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private static Integer nullablePositive(Integer value) {
        return value == null || value <= 0 ? null : value;
    }

    private static Integer nullableNonNegative(Integer value) {
        return value == null || value < 0 ? null : value;
    }

    private static int nonNegative(Integer value) {
        return Math.max(value == null ? 0 : value, 0);
    }

    private static String blankToNull(String value) {
        String s = Objects.toString(value, "").trim();
        return s.isBlank() ? null : s;
    }

    private static String defaultText(String value, String fallback) {
        String s = Objects.toString(value, "").trim();
        return s.isBlank() ? fallback : s;
    }

    private static String normalizeContextCompactionProvider(String provider) {
        String value = defaultText(provider, "openai").toLowerCase();
        return "openai".equals(value) ? value : "openai";
    }

    private static String normalizeContextCompactionMode(String mode) {
        String value = defaultText(mode, "auto").toLowerCase();
        return "manual".equals(value) ? "manual" : "auto";
    }

    private static String normalizeContextCompactionStatus(String status) {
        String value = defaultText(status, "active").toLowerCase();
        return switch (value) {
            case "expired", "failed" -> value;
            default -> "active";
        };
    }

    @Value
    @Builder
    public static class RunEntry {
        String runId;
        Long userId;
        Long sessionId;
        Long projectId;
        String goal;
        String status;
        String model;
        OffsetDateTime startedAt;
        String metadataJson;
    }

    @Value
    @Builder
    public static class FinishRunEntry {
        String runId;
        String status;
        String errorMessage;
        OffsetDateTime endedAt;
        Integer totalModelCalls;
        Integer totalToolCalls;
        Integer totalToolResultOpenCalls;
        Integer inputTokens;
        Integer outputTokens;
        Integer totalTokens;
        String metadataJson;
    }

    @Value
    @Builder
    public static class ModelCallEntry {
        String runId;
        Long userId;
        Long sessionId;
        Long projectId;
        String provider;
        String callSite;
        String model;
        String responseId;
        Integer inputItems;
        Integer estimatedInputChars;
        Integer instructionsChars;
        Integer toolsCount;
        Integer maxOutputTokens;
        String serviceTier;
        Integer attempt;
        Boolean success;
        Integer inputTokens;
        Integer outputTokens;
        Integer totalTokens;
        Integer elapsedMs;
        String errorType;
        String errorMessage;
        String rawUsageJson;
        String metadataJson;
    }

    @Value
    @Builder
    public static class ContextCompactionEntry {
        String runId;
        Long userId;
        Long sessionId;
        Long projectId;
        String provider;
        String model;
        String mode;
        String status;
        String contextFormat;
        Integer sourceFirstSeq;
        Integer sourceLastSeq;
        Integer sourceFirstItemIdx;
        Integer sourceLastItemIdx;
        String instructionsHash;
        String inputFingerprint;
        Integer compactThreshold;
        String compactedInputJson;
        Integer compactedItemCount;
        Integer estimatedInputChars;
        Integer estimatedSavedChars;
        String responseId;
        String previousResponseId;
        String rawUsageJson;
        String metadataJson;
        String errorType;
        String errorMessage;
    }

    @Value
    @Builder
    public static class ToolCallEntry {
        String runId;
        String callId;
        Long userId;
        Long sessionId;
        Long projectId;
        String toolName;
        String argumentsJson;
        String status;
        Boolean success;
        String resultSummary;
        String errorCode;
        Integer resultChars;
        String readKey;
        String duplicateOfCallId;
        Integer costMs;
        OffsetDateTime startedAt;
        OffsetDateTime finishedAt;
        String resultJson;
        Boolean compactedForModel;
    }

    @Value
    @Builder
    public static class LedgerSnapshotEntry {
        String runId;
        Long userId;
        Long sessionId;
        Long projectId;
        String ledgerJson;
        Integer modelCalls;
        Integer toolCalls;
        Integer toolResultOpenCalls;
    }
}
