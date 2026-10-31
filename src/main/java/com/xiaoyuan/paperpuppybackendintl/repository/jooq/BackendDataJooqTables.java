package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Table;
import org.jooq.impl.DSL;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.jooq.impl.SQLDataType.BIGINT;
import static org.jooq.impl.SQLDataType.BOOLEAN;
import static org.jooq.impl.SQLDataType.CLOB;
import static org.jooq.impl.SQLDataType.INTEGER;
import static org.jooq.impl.SQLDataType.JSONB;
import static org.jooq.impl.SQLDataType.LOCALDATE;
import static org.jooq.impl.SQLDataType.NUMERIC;
import static org.jooq.impl.SQLDataType.OFFSETDATETIME;

/**
 * 对现有业务表的一小部分“手写 table/field 定义”，用于在本机没起 DB / 跳过 codegen 时依然可编译。
 *
 * <p>默认 DataSource 已设置 currentSchema=backend_data，因此这里不显式写 schema。</p>
 */
public final class BackendDataJooqTables {

    private BackendDataJooqTables() {
    }

    // ---------- file_derivatives ----------
    public static final Table<?> FILE_DERIVATIVES = DSL.table(DSL.name("file_derivatives"));
    public static final Field<Long> FD_FILE_ID = DSL.field(DSL.name("file_id"), BIGINT.nullable(false));
    public static final Field<String> FD_TYPE = DSL.field(DSL.name("type"), CLOB.nullable(false));
    public static final Field<String> FD_STATUS = DSL.field(DSL.name("status"), CLOB.nullable(false));
    public static final Field<String> FD_STORAGE_TYPE = DSL.field(DSL.name("storage_type"), CLOB);
    public static final Field<String> FD_BUCKET = DSL.field(DSL.name("bucket"), CLOB);
    public static final Field<String> FD_OBJECT_KEY = DSL.field(DSL.name("object_key"), CLOB);
    public static final Field<String> FD_CONTENT_TEXT = DSL.field(DSL.name("content_text"), CLOB);
    public static final Field<JSONB> FD_METADATA = DSL.field(DSL.name("metadata"), JSONB);
    public static final Field<Boolean> FD_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));

    // ---------- pdf_derived_media_asset_cache ----------
    public static final Table<?> PDF_DERIVED_MEDIA_ASSET_CACHE = DSL.table(DSL.name("pdf_derived_media_asset_cache"));
    public static final Field<Long> PDMAC_ID = DSL.field(DSL.name("pdf_derived_media_asset_cache", "id"), BIGINT.nullable(false));
    public static final Field<Long> PDMAC_OWNER_USER_ID = DSL.field(DSL.name("pdf_derived_media_asset_cache", "owner_user_id"), BIGINT.nullable(false));
    public static final Field<String> PDMAC_SOURCE_CONTENT_HASH = DSL.field(DSL.name("pdf_derived_media_asset_cache", "source_content_hash"), CLOB.nullable(false));
    public static final Field<Long> PDMAC_SOURCE_FILE_ID = DSL.field(DSL.name("pdf_derived_media_asset_cache", "source_file_id"), BIGINT);
    public static final Field<String> PDMAC_EXTRACTOR_SIGNATURE = DSL.field(DSL.name("pdf_derived_media_asset_cache", "extractor_signature"), CLOB.nullable(false));
    public static final Field<Long> PDMAC_DERIVED_FILE_ID = DSL.field(DSL.name("pdf_derived_media_asset_cache", "derived_file_id"), BIGINT);
    public static final Field<String> PDMAC_KIND = DSL.field(DSL.name("pdf_derived_media_asset_cache", "kind"), CLOB.nullable(false));
    public static final Field<String> PDMAC_PROVIDER = DSL.field(DSL.name("pdf_derived_media_asset_cache", "provider"), CLOB.nullable(false));
    public static final Field<Integer> PDMAC_PAGE_NUMBER = DSL.field(DSL.name("pdf_derived_media_asset_cache", "page_number"), INTEGER.nullable(false));
    public static final Field<Integer> PDMAC_ASSET_INDEX = DSL.field(DSL.name("pdf_derived_media_asset_cache", "asset_index"), INTEGER.nullable(false));
    public static final Field<JSONB> PDMAC_BBOX = DSL.field(DSL.name("pdf_derived_media_asset_cache", "bbox"), JSONB);
    public static final Field<String> PDMAC_CAPTION = DSL.field(DSL.name("pdf_derived_media_asset_cache", "caption"), CLOB);
    public static final Field<String> PDMAC_DESCRIPTION = DSL.field(DSL.name("pdf_derived_media_asset_cache", "description"), CLOB);
    public static final Field<String> PDMAC_CONTENT_HASH = DSL.field(DSL.name("pdf_derived_media_asset_cache", "content_hash"), CLOB);
    public static final Field<BigDecimal> PDMAC_SCORE = DSL.field(DSL.name("pdf_derived_media_asset_cache", "score"), NUMERIC);
    public static final Field<String> PDMAC_STATUS = DSL.field(DSL.name("pdf_derived_media_asset_cache", "status"), CLOB.nullable(false));
    public static final Field<String> PDMAC_ERROR_MESSAGE = DSL.field(DSL.name("pdf_derived_media_asset_cache", "error_message"), CLOB);
    public static final Field<JSONB> PDMAC_METADATA = DSL.field(DSL.name("pdf_derived_media_asset_cache", "metadata"), JSONB.nullable(false));
    public static final Field<OffsetDateTime> PDMAC_CREATED_AT = DSL.field(DSL.name("pdf_derived_media_asset_cache", "created_at"), OFFSETDATETIME);
    public static final Field<OffsetDateTime> PDMAC_UPDATED_AT = DSL.field(DSL.name("pdf_derived_media_asset_cache", "updated_at"), OFFSETDATETIME);
    public static final Field<Boolean> PDMAC_IS_DELETED = DSL.field(DSL.name("pdf_derived_media_asset_cache", "is_deleted"), BOOLEAN.nullable(false));

    // ---------- project_pdf_derived_media_asset_links ----------
    public static final Table<?> PROJECT_PDF_DERIVED_MEDIA_ASSET_LINKS = DSL.table(DSL.name("project_pdf_derived_media_asset_links"));
    public static final Field<Long> PPDMAL_ID = DSL.field(DSL.name("project_pdf_derived_media_asset_links", "id"), BIGINT.nullable(false));
    public static final Field<Long> PPDMAL_PROJECT_ID = DSL.field(DSL.name("project_pdf_derived_media_asset_links", "project_id"), BIGINT.nullable(false));
    public static final Field<Long> PPDMAL_SOURCE_FILE_ID = DSL.field(DSL.name("project_pdf_derived_media_asset_links", "source_file_id"), BIGINT.nullable(false));
    public static final Field<Long> PPDMAL_CACHE_ASSET_ID = DSL.field(DSL.name("project_pdf_derived_media_asset_links", "cache_asset_id"), BIGINT.nullable(false));
    public static final Field<Long> PPDMAL_CREATED_BY = DSL.field(DSL.name("project_pdf_derived_media_asset_links", "created_by"), BIGINT);
    public static final Field<OffsetDateTime> PPDMAL_CREATED_AT = DSL.field(DSL.name("project_pdf_derived_media_asset_links", "created_at"), OFFSETDATETIME);
    public static final Field<OffsetDateTime> PPDMAL_UPDATED_AT = DSL.field(DSL.name("project_pdf_derived_media_asset_links", "updated_at"), OFFSETDATETIME);
    public static final Field<Boolean> PPDMAL_IS_DELETED = DSL.field(DSL.name("project_pdf_derived_media_asset_links", "is_deleted"), BOOLEAN.nullable(false));

    // ---------- file_text_fragments ----------
    public static final Table<?> FILE_TEXT_FRAGMENTS = DSL.table(DSL.name("file_text_fragments"));
    public static final Field<Long> FTF_FILE_ID = DSL.field(DSL.name("file_id"), BIGINT.nullable(false));
    public static final Field<Integer> FTF_FRAGMENT_INDEX = DSL.field(DSL.name("fragment_index"), INTEGER.nullable(false));
    public static final Field<String> FTF_FRAGMENT_CONTENT = DSL.field(DSL.name("fragment_content"), CLOB.nullable(false));
    public static final Field<JSONB> FTF_RAW_CONTENT = DSL.field(DSL.name("raw_content"), JSONB);
    public static final Field<String> FTF_MESSAGE_TYPE = DSL.field(DSL.name("message_type"), CLOB);
    public static final Field<Integer> FTF_TOTAL_FRAGMENTS = DSL.field(DSL.name("total_fragments"), INTEGER);
    public static final Field<Boolean> FTF_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));

    // ---------- object_storage_files ----------
    public static final Table<?> OBJECT_STORAGE_FILES = DSL.table(DSL.name("object_storage_files"));
    public static final Field<Long> OSF_ID = DSL.field(DSL.name("id"), BIGINT.nullable(false));
    public static final Field<String> OSF_SOURCE_TYPE = DSL.field(DSL.name("source_type"), CLOB);
    public static final Field<String> OSF_SOURCE_KEY = DSL.field(DSL.name("source_key"), CLOB);
    public static final Field<String> OSF_STORAGE_TYPE = DSL.field(DSL.name("storage_type"), CLOB);
    public static final Field<String> OSF_BUCKET = DSL.field(DSL.name("bucket"), CLOB);
    public static final Field<String> OSF_OBJECT_KEY = DSL.field(DSL.name("object_key"), CLOB);
    public static final Field<String> OSF_PUBLIC_DOMAIN = DSL.field(DSL.name("public_domain"), CLOB);
    public static final Field<String> OSF_PUBLIC_PATH = DSL.field(DSL.name("public_path"), CLOB);
    public static final Field<String> OSF_MIME_TYPE = DSL.field(DSL.name("mime_type"), CLOB);
    public static final Field<String> OSF_FILE_EXT = DSL.field(DSL.name("file_ext"), CLOB);
    public static final Field<Long> OSF_SIZE_BYTES = DSL.field(DSL.name("size_bytes"), BIGINT);
    public static final Field<String> OSF_ORIGINAL_NAME = DSL.field(DSL.name("original_name"), CLOB);
    public static final Field<String> OSF_ETAG = DSL.field(DSL.name("etag"), CLOB);
    public static final Field<String> OSF_VERSION_ID = DSL.field(DSL.name("version_id"), CLOB);
    public static final Field<OffsetDateTime> OSF_LAST_MODIFIED = DSL.field(DSL.name("last_modified"), OFFSETDATETIME);
    public static final Field<String> OSF_STATUS = DSL.field(DSL.name("status"), CLOB);
    public static final Field<String> OSF_SOURCE_ID = DSL.field(DSL.name("source_id"), CLOB);
    public static final Field<JSONB> OSF_METADATA = DSL.field(DSL.name("metadata"), JSONB);
    public static final Field<OffsetDateTime> OSF_CREATED_AT = DSL.field(DSL.name("created_at"), OFFSETDATETIME);
    public static final Field<OffsetDateTime> OSF_UPDATED_AT = DSL.field(DSL.name("updated_at"), OFFSETDATETIME);
    public static final Field<Boolean> OSF_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));

    // ---------- project_file_links ----------
    public static final Table<?> PROJECT_FILE_LINKS = DSL.table(DSL.name("project_file_links"));
    public static final Field<Long> PFL_ID = DSL.field(DSL.name("id"), BIGINT.nullable(false));
    public static final Field<Long> PFL_PROJECT_ID = DSL.field(DSL.name("project_id"), BIGINT.nullable(false));
    public static final Field<Long> PFL_FILE_ID = DSL.field(DSL.name("file_id"), BIGINT.nullable(false));
    public static final Field<Long> PFL_ADDED_BY = DSL.field(DSL.name("added_by"), BIGINT);
    public static final Field<String> PFL_DISPLAY_NAME = DSL.field(DSL.name("display_name"), CLOB);
    public static final Field<String> PFL_KIND = DSL.field(DSL.name("kind"), CLOB);
    public static final Field<Boolean> PFL_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));
    public static final Field<OffsetDateTime> PFL_CREATED_AT = DSL.field(DSL.name("created_at"), OFFSETDATETIME);
    public static final Field<OffsetDateTime> PFL_UPDATED_AT = DSL.field(DSL.name("updated_at"), OFFSETDATETIME);

    // ---------- user_devices ----------
    public static final Table<?> USER_DEVICES = DSL.table(DSL.name("user_devices"));
    public static final Field<Long> UD_ID = DSL.field(DSL.name("id"), BIGINT.nullable(false));
    public static final Field<Long> UD_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    public static final Field<String> UD_DEVICE_TOKEN = DSL.field(DSL.name("device_token"), CLOB.nullable(false));
    public static final Field<String> UD_DEVICE_TYPE = DSL.field(DSL.name("device_type"), CLOB.nullable(false));
    public static final Field<String> UD_ENVIRONMENT = DSL.field(DSL.name("environment"), CLOB);
    public static final Field<OffsetDateTime> UD_LAST_ACTIVE_AT = DSL.field(DSL.name("last_active_at"), OFFSETDATETIME);
    public static final Field<OffsetDateTime> UD_CREATED_AT = DSL.field(DSL.name("created_at"), OFFSETDATETIME);

    // ---------- user_checkin_status ----------
    public static final Table<?> USER_CHECKIN_STATUS = DSL.table(DSL.name("user_checkin_status"));
    public static final Field<Long> UCS_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    public static final Field<Integer> UCS_STREAK_COUNT = DSL.field(DSL.name("streak_count"), INTEGER.nullable(false));
    public static final Field<Integer> UCS_LONGEST_STREAK = DSL.field(DSL.name("longest_streak"), INTEGER.nullable(false));
    public static final Field<java.time.LocalDate> UCS_LAST_CHECKIN_DATE = DSL.field(DSL.name("last_checkin_date"), LOCALDATE);
    public static final Field<OffsetDateTime> UCS_CREATED_AT = DSL.field(DSL.name("created_at"), OFFSETDATETIME);
    public static final Field<OffsetDateTime> UCS_UPDATED_AT = DSL.field(DSL.name("updated_at"), OFFSETDATETIME);
    public static final Field<Boolean> UCS_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));

    // ---------- user_checkin_log ----------
    public static final Table<?> USER_CHECKIN_LOG = DSL.table(DSL.name("user_checkin_log"));
    public static final Field<Long> UCL_ID = DSL.field(DSL.name("id"), BIGINT.nullable(false));
    public static final Field<Long> UCL_USER_ID = DSL.field(DSL.name("user_id"), BIGINT.nullable(false));
    public static final Field<java.time.LocalDate> UCL_CHECKIN_DATE = DSL.field(DSL.name("checkin_date"), LOCALDATE.nullable(false));
    public static final Field<String> UCL_PLATFORM = DSL.field(DSL.name("platform"), CLOB);
    public static final Field<String> UCL_APP_VERSION = DSL.field(DSL.name("app_version"), CLOB);
    public static final Field<String> UCL_DEVICE_TYPE = DSL.field(DSL.name("device_type"), CLOB);
    public static final Field<String> UCL_DEVICE_TOKEN = DSL.field(DSL.name("device_token"), CLOB);
    public static final Field<Integer> UCL_REWARD_POINTS = DSL.field(DSL.name("reward_points"), INTEGER.nullable(false));
    public static final Field<Integer> UCL_STREAK_AFTER = DSL.field(DSL.name("streak_after"), INTEGER.nullable(false));
    public static final Field<String> UCL_IP_ADDRESS = DSL.field(DSL.name("ip_address"), CLOB);
    public static final Field<String> UCL_USER_AGENT = DSL.field(DSL.name("user_agent"), CLOB);
    public static final Field<OffsetDateTime> UCL_CREATED_AT = DSL.field(DSL.name("created_at"), OFFSETDATETIME);
    public static final Field<Boolean> UCL_IS_DELETED = DSL.field(DSL.name("is_deleted"), BOOLEAN.nullable(false));
}


