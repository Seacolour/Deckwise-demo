package com.xiaoyuan.paperpuppybackendintl.repository;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

import static org.jooq.impl.SQLDataType.BIGINT;
import static org.jooq.impl.SQLDataType.BOOLEAN;
import static org.jooq.impl.SQLDataType.CLOB;
import static org.jooq.impl.SQLDataType.INTEGER;
import static org.jooq.impl.SQLDataType.JSONB;
import static org.jooq.impl.SQLDataType.OFFSETDATETIME;

/**
 * AI usage 账本仓储。
 *
 * <p>使用动态 DSL，避免每次新增账本字段都依赖 jOOQ codegen。</p>
 */
@Repository
@RequiredArgsConstructor
public class AiUsageLedgerRepository {

    private static final Table<?> AI_USAGE_LEDGER = DSL.table(DSL.name("ai_usage_ledger"));

    private static final Field<String> AUL_USAGE_KEY = DSL.field(DSL.name("usage_key"), CLOB.nullable(false));
    private static final Field<String> AUL_USAGE_SOURCE = DSL.field(DSL.name("usage_source"), CLOB.nullable(false));
    private static final Field<Long> AUL_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    private static final Field<Long> AUL_SESSION_ID = DSL.field(DSL.name("session_id"), BIGINT);
    private static final Field<Long> AUL_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT);
    private static final Field<String> AUL_FEATURE_TYPE = DSL.field(DSL.name("feature_type"), CLOB);
    private static final Field<String> AUL_REPORT_STATUS = DSL.field(DSL.name("report_status"), CLOB.nullable(false));
    private static final Field<String> AUL_SETTLEMENT_KEY = DSL.field(DSL.name("settlement_key"), CLOB.nullable(false));
    private static final Field<String> AUL_PROVIDER = DSL.field(DSL.name("provider"), CLOB.nullable(false));
    private static final Field<String> AUL_PROVIDER_REQUEST_ID = DSL.field(DSL.name("provider_request_id"), CLOB);
    private static final Field<String> AUL_REQUESTED_MODEL = DSL.field(DSL.name("requested_model"), CLOB);
    private static final Field<String> AUL_ACTUAL_MODEL = DSL.field(DSL.name("actual_model"), CLOB);
    private static final Field<Boolean> AUL_SUCCESS = DSL.field(DSL.name("success"), BOOLEAN);
    private static final Field<Integer> AUL_INPUT_TOKENS = DSL.field(DSL.name("input_tokens"), INTEGER);
    private static final Field<Integer> AUL_OUTPUT_TOKENS = DSL.field(DSL.name("output_tokens"), INTEGER);
    private static final Field<Integer> AUL_CACHED_TOKENS = DSL.field(DSL.name("cached_tokens"), INTEGER);
    private static final Field<Integer> AUL_REASONING_TOKENS = DSL.field(DSL.name("reasoning_tokens"), INTEGER);
    private static final Field<Integer> AUL_TOTAL_TOKENS = DSL.field(DSL.name("total_tokens"), INTEGER);
    private static final Field<Integer> AUL_LATENCY_MS = DSL.field(DSL.name("latency_ms"), INTEGER);
    private static final Field<Integer> AUL_BILLABLE_INPUT_TOKENS = DSL.field(DSL.name("billable_input_tokens"), INTEGER);
    private static final Field<Integer> AUL_BILLABLE_CACHED_TOKENS = DSL.field(DSL.name("billable_cached_tokens"), INTEGER);
    private static final Field<Integer> AUL_BILLABLE_OUTPUT_TOKENS = DSL.field(DSL.name("billable_output_tokens"), INTEGER);
    private static final Field<String> AUL_CURRENCY = DSL.field(DSL.name("currency"), CLOB);
    private static final Field<Long> AUL_INPUT_RATE_MICROS_PER_MILLION = DSL.field(DSL.name("input_rate_micros_per_million"), BIGINT);
    private static final Field<Long> AUL_CACHED_INPUT_RATE_MICROS_PER_MILLION = DSL.field(DSL.name("cached_input_rate_micros_per_million"), BIGINT);
    private static final Field<Long> AUL_OUTPUT_RATE_MICROS_PER_MILLION = DSL.field(DSL.name("output_rate_micros_per_million"), BIGINT);
    private static final Field<Long> AUL_ESTIMATED_INPUT_COST_MICROS = DSL.field(DSL.name("estimated_input_cost_micros"), BIGINT);
    private static final Field<Long> AUL_ESTIMATED_CACHED_COST_MICROS = DSL.field(DSL.name("estimated_cached_cost_micros"), BIGINT);
    private static final Field<Long> AUL_ESTIMATED_OUTPUT_COST_MICROS = DSL.field(DSL.name("estimated_output_cost_micros"), BIGINT);
    private static final Field<Long> AUL_ESTIMATED_TOTAL_COST_MICROS = DSL.field(DSL.name("estimated_total_cost_micros"), BIGINT);
    private static final Field<org.jooq.JSONB> AUL_RAW_USAGE = DSL.field(DSL.name("raw_usage"), JSONB);
    private static final Field<org.jooq.JSONB> AUL_METADATA = DSL.field(DSL.name("metadata"), JSONB);
    private static final Field<OffsetDateTime> AUL_SETTLED_AT = DSL.field(DSL.name("settled_at"), OFFSETDATETIME.nullable(false));

    private final DSLContext dsl;

    public void upsert(UsageLedgerEntry entry) {
        dsl.insertInto(AI_USAGE_LEDGER)
                .set(AUL_USAGE_KEY, entry.getUsageKey())
                .set(AUL_USAGE_SOURCE, entry.getUsageSource())
                .set(AUL_USER_ID, entry.getUserId())
                .set(AUL_SESSION_ID, entry.getSessionId())
                .set(AUL_PROJECT_ID, entry.getProjectId())
                .set(AUL_FEATURE_TYPE, entry.getFeatureType())
                .set(AUL_REPORT_STATUS, entry.getReportStatus())
                .set(AUL_SETTLEMENT_KEY, entry.getSettlementKey())
                .set(AUL_PROVIDER, entry.getProvider())
                .set(AUL_PROVIDER_REQUEST_ID, entry.getProviderRequestId())
                .set(AUL_REQUESTED_MODEL, entry.getRequestedModel())
                .set(AUL_ACTUAL_MODEL, entry.getActualModel())
                .set(AUL_SUCCESS, entry.getSuccess())
                .set(AUL_INPUT_TOKENS, entry.getInputTokens())
                .set(AUL_OUTPUT_TOKENS, entry.getOutputTokens())
                .set(AUL_CACHED_TOKENS, entry.getCachedTokens())
                .set(AUL_REASONING_TOKENS, entry.getReasoningTokens())
                .set(AUL_TOTAL_TOKENS, entry.getTotalTokens())
                .set(AUL_LATENCY_MS, entry.getLatencyMs())
                .set(AUL_BILLABLE_INPUT_TOKENS, entry.getBillableInputTokens())
                .set(AUL_BILLABLE_CACHED_TOKENS, entry.getBillableCachedTokens())
                .set(AUL_BILLABLE_OUTPUT_TOKENS, entry.getBillableOutputTokens())
                .set(AUL_CURRENCY, entry.getCurrency())
                .set(AUL_INPUT_RATE_MICROS_PER_MILLION, entry.getInputRateMicrosPerMillion())
                .set(AUL_CACHED_INPUT_RATE_MICROS_PER_MILLION, entry.getCachedInputRateMicrosPerMillion())
                .set(AUL_OUTPUT_RATE_MICROS_PER_MILLION, entry.getOutputRateMicrosPerMillion())
                .set(AUL_ESTIMATED_INPUT_COST_MICROS, entry.getEstimatedInputCostMicros())
                .set(AUL_ESTIMATED_CACHED_COST_MICROS, entry.getEstimatedCachedCostMicros())
                .set(AUL_ESTIMATED_OUTPUT_COST_MICROS, entry.getEstimatedOutputCostMicros())
                .set(AUL_ESTIMATED_TOTAL_COST_MICROS, entry.getEstimatedTotalCostMicros())
                .set(AUL_RAW_USAGE, entry.getRawUsageJson() == null ? null : org.jooq.JSONB.valueOf(entry.getRawUsageJson()))
                .set(AUL_METADATA, entry.getMetadataJson() == null ? null : org.jooq.JSONB.valueOf(entry.getMetadataJson()))
                .set(AUL_SETTLED_AT, entry.getSettledAt())
                .onConflict(AUL_USAGE_KEY)
                .doUpdate()
                .set(AUL_USAGE_SOURCE, entry.getUsageSource())
                .set(AUL_USER_ID, entry.getUserId())
                .set(AUL_SESSION_ID, entry.getSessionId())
                .set(AUL_PROJECT_ID, entry.getProjectId())
                .set(AUL_FEATURE_TYPE, entry.getFeatureType())
                .set(AUL_REPORT_STATUS, entry.getReportStatus())
                .set(AUL_SETTLEMENT_KEY, entry.getSettlementKey())
                .set(AUL_PROVIDER, entry.getProvider())
                .set(AUL_PROVIDER_REQUEST_ID, entry.getProviderRequestId())
                .set(AUL_REQUESTED_MODEL, entry.getRequestedModel())
                .set(AUL_ACTUAL_MODEL, entry.getActualModel())
                .set(AUL_SUCCESS, entry.getSuccess())
                .set(AUL_INPUT_TOKENS, entry.getInputTokens())
                .set(AUL_OUTPUT_TOKENS, entry.getOutputTokens())
                .set(AUL_CACHED_TOKENS, entry.getCachedTokens())
                .set(AUL_REASONING_TOKENS, entry.getReasoningTokens())
                .set(AUL_TOTAL_TOKENS, entry.getTotalTokens())
                .set(AUL_LATENCY_MS, entry.getLatencyMs())
                .set(AUL_BILLABLE_INPUT_TOKENS, entry.getBillableInputTokens())
                .set(AUL_BILLABLE_CACHED_TOKENS, entry.getBillableCachedTokens())
                .set(AUL_BILLABLE_OUTPUT_TOKENS, entry.getBillableOutputTokens())
                .set(AUL_CURRENCY, entry.getCurrency())
                .set(AUL_INPUT_RATE_MICROS_PER_MILLION, entry.getInputRateMicrosPerMillion())
                .set(AUL_CACHED_INPUT_RATE_MICROS_PER_MILLION, entry.getCachedInputRateMicrosPerMillion())
                .set(AUL_OUTPUT_RATE_MICROS_PER_MILLION, entry.getOutputRateMicrosPerMillion())
                .set(AUL_ESTIMATED_INPUT_COST_MICROS, entry.getEstimatedInputCostMicros())
                .set(AUL_ESTIMATED_CACHED_COST_MICROS, entry.getEstimatedCachedCostMicros())
                .set(AUL_ESTIMATED_OUTPUT_COST_MICROS, entry.getEstimatedOutputCostMicros())
                .set(AUL_ESTIMATED_TOTAL_COST_MICROS, entry.getEstimatedTotalCostMicros())
                .set(AUL_RAW_USAGE, entry.getRawUsageJson() == null ? null : org.jooq.JSONB.valueOf(entry.getRawUsageJson()))
                .set(AUL_METADATA, entry.getMetadataJson() == null ? null : org.jooq.JSONB.valueOf(entry.getMetadataJson()))
                .set(AUL_SETTLED_AT, entry.getSettledAt())
                .execute();
    }

    @Data
    public static class UsageLedgerEntry {
        private String usageKey;
        private String usageSource;
        private Long userId;
        private Long sessionId;
        private Long projectId;
        private String featureType;
        private String reportStatus;
        private String settlementKey;
        private String provider;
        private String providerRequestId;
        private String requestedModel;
        private String actualModel;
        private Boolean success;
        private Integer inputTokens;
        private Integer outputTokens;
        private Integer cachedTokens;
        private Integer reasoningTokens;
        private Integer totalTokens;
        private Integer latencyMs;
        private Integer billableInputTokens;
        private Integer billableCachedTokens;
        private Integer billableOutputTokens;
        private String currency;
        private Long inputRateMicrosPerMillion;
        private Long cachedInputRateMicrosPerMillion;
        private Long outputRateMicrosPerMillion;
        private Long estimatedInputCostMicros;
        private Long estimatedCachedCostMicros;
        private Long estimatedOutputCostMicros;
        private Long estimatedTotalCostMicros;
        private String rawUsageJson;
        private String metadataJson;
        private OffsetDateTime settledAt;
    }
}
