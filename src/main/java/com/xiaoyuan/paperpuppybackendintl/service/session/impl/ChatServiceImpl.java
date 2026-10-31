package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.grpc.client.model.AiChatRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.UserMessageSendRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.model.entity.SessionMessage;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageRole;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageStatus;
import com.xiaoyuan.paperpuppybackendintl.model.enums.SessionMessageType;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionMessageRepository;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectService;
import com.xiaoyuan.paperpuppybackendintl.service.session.AutoTitleService;
import com.xiaoyuan.paperpuppybackendintl.service.session.ChatService;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionService;
import com.xiaoyuan.paperpuppybackendintl.service.session.model.ChatExecutionPolicy;
import com.xiaoyuan.paperpuppybackendintl.service.session.policy.ChatExecutionPolicyResolver;
import com.xiaoyuan.paperpuppybackendintl.service.session.util.FileQuoteParser;
import com.xiaoyuan.paperpuppybackendintl.service.session.util.FileQuoteResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;

/**
 * 对话 Service（实现类，SSE + gRPC）
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final SessionService sessionService;
    private final AutoTitleService autoTitleService;
    private final SessionMessageRepository sessionMessageRepository;
    private final ChatAttachmentService chatAttachmentService;
    private final ChatContextAssembler chatContextAssembler;
    private final AgentRequestFactory agentRequestFactory;
    private final ChatExecutionLifecycle chatExecutionLifecycle;
    private final AgentStreamCoordinator agentStreamCoordinator;
    private final ChatExecutionPolicyResolver executionPolicyResolver;
    @Qualifier("chatExecutor")
    private final TaskExecutor chatExecutor;
    private final ProjectService projectService;
    private final FileQuoteResolver fileQuoteResolver;

    // 图片
    @SuppressWarnings("unused")
    private static final String DEFAULT_IMAGE_PROMPT = "请描述图片内容，并结合上下文回答用户可能关心的问题。";

    // 文件
    @SuppressWarnings("unused")
    private static final String DEFAULT_FILE_PROMPT = "请解析以上文件内容，提取关键信息，并给出结构化总结。";

    // 图片 + 文件
    @SuppressWarnings("unused")
    private static final String DEFAULT_MIXED_PROMPT = "请结合图片和文件内容进行分析，提取关键信息并回答用户问题。";
    @Override
    public SseEmitter chat(Long userId, Long sessionId, UserMessageSendRequest req) {
        return chatStreamingInternal(userId, sessionId, req);
    }

    @Override
    public void abort(Long userId, Long sessionId) {
        chatExecutionLifecycle.abort(userId, sessionId);
    }

    private SseEmitter chatStreamingInternal(Long userId, Long sessionId, UserMessageSendRequest req) {
        Session session = sessionService.requireSession(userId, sessionId);
        ChatExecutionPolicy executionPolicy = executionPolicyResolver.resolve(req);
        String content = req == null ? null : req.getMessage();
        List<String> imageKeys = req == null ? null : req.getImageCacheKeys();
        // 项目文件引用
        List<String> fileCacheKeys = req == null ? null : req.getFileCacheKeys();
        List<String> requestedAllFileKeys = fileCacheKeys != null ? new ArrayList<>(fileCacheKeys) : new ArrayList<>();
        boolean hasText = content != null && !content.isBlank();
        boolean hasImage = imageKeys != null && !imageKeys.isEmpty();
        boolean hasFile = !requestedAllFileKeys.isEmpty();

        log.info(
                "chat execution policy resolved: userId={}, sessionId={}, requestedMode={}, resolvedRoute={}, allowAgentSkills={}",
                userId,
                session.getId(),
                executionPolicy.getRequestedMode(),
                executionPolicy.getResolvedRoute(),
                executionPolicy.isAllowAgentSkills());
        if (!hasText && !hasImage && !hasFile) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        }

        if (requestedAllFileKeys.size() > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多支持同时引用 5 个文件");
        }
        if (imageKeys != null && imageKeys.size() > 5) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "最多支持同时引用 5 张图片");
        }

        Optional<ChatExecutionLifecycle.Handle> lifecycleHandle = chatExecutionLifecycle.tryAcquire(
                userId, session.getId());
        if (lifecycleHandle.isEmpty()) {
            return immediateErrorEmitter("该会话正在回复中，请稍后再试");
        }
        ChatExecutionLifecycle.Handle executionHandle = lifecycleHandle.orElseThrow();
        chatExecutionLifecycle.prepareForStart(executionHandle);
        final String requestedServiceTier = executionPolicy.isUseOpenAiPriorityProcessing() ? "priority" : "default";
        final AgentRequestFactory.ModelRoute modelRoute;
        try {
            modelRoute = agentRequestFactory.resolveModelRoute(req, userId, session.getId());
        } catch (BusinessException e) {
            chatExecutionLifecycle.releaseAfterPreparationFailure(executionHandle);
            return immediateErrorEmitter(e.getMessage());
        } catch (RuntimeException | Error e) {
            chatExecutionLifecycle.releaseAfterPreparationFailure(executionHandle);
            throw e;
        }

        // 1) 拉取历史（不包含本轮 user 输入；本轮输入将通过 gRPC 的 message 字段单独传递）
        // 注意：这里需要“最近的 200 条”，否则会话变长后会把最新上下文丢掉
        List<SessionMessage> history = sessionMessageRepository.listRecentBySession(session.getId(), 200);

        // 2) 解析内联文件引用标记 @pp[...]{...}，分离 rawContent / content
        //    rawContent = 原始 message（保留标记，供前端渲染引用块）
        //    userContent = AI 可读版本（标记替换为锚点提示 / 注入 fragment）
        String userRaw = content;       // 原始 message → rawContent（前端渲染用）
        String userContent = content;   // AI 可读版本 → content（历史回灌用）+ message（本轮 AI 输入）
        FileQuoteParser.ParseResult quoteParseResult = FileQuoteParser.parse(content);
        if (quoteParseResult.hasQuotes()) {
            try {
                userContent = fileQuoteResolver.resolve(quoteParseResult);
                log.info("File quotes resolved: userId={}, sessionId={}, quoteCount={}, rawLen={}, resolvedLen={}",
                        userId, session.getId(), quoteParseResult.quotes().size(),
                        safeLength(userRaw), safeLength(userContent));
            } catch (Exception e) {
                log.warn("File quote resolution failed, fallback to raw message: userId={}, sessionId={}, error={}",
                        userId, session.getId(), e.getMessage());
                userContent = content; // 解析失败时退化为原始文本
            }
        }

        final Long userMessageId;
        final ChatAttachmentService.FileContextResult fc;
        final String fullContextJson;
        try {
            // 3) 方案B：会话级默认携带附件（文件/图片）
            // - 将本轮新选中的附件“元信息”先完成校验，确保通过后再真正写入历史
            // - 文件分片仍然只在每轮调用时按需从 DB 读取并注入上下文，不写入历史（避免历史膨胀）
            List<ChatAttachmentService.AttachmentMeta> metas = chatAttachmentService.extractAttachmentMetas(history);
            List<String> historyFileKeys = new ArrayList<>();
            List<String> historyImageKeys = new ArrayList<>();
            for (ChatAttachmentService.AttachmentMeta meta : metas) {
                if ("image".equalsIgnoreCase(meta.kind())) {
                    historyImageKeys.add(meta.cacheKey());
                } else if ("file".equalsIgnoreCase(meta.kind())) {
                    historyFileKeys.add(meta.cacheKey());
                }
            }

            ChatAttachmentService.PreparedAttachmentWrites preparedAttachmentWrites = chatAttachmentService.prepareAttachmentWrites(
                    userId,
                    session,
                    requestedAllFileKeys,
                    imageKeys,
                    historyFileKeys,
                    historyImageKeys);

            // 本轮有效 keys（历史默认携带 + 本轮追加），仍限制最多 5（避免成本不可控）
            List<String> effectiveFileKeys = chatAttachmentService.mergeRecentKeys(
                    historyFileKeys, preparedAttachmentWrites.newFileKeys(), 5);
            List<String> effectiveImageKeys = chatAttachmentService.mergeRecentKeys(
                    historyImageKeys, preparedAttachmentWrites.newImageKeys(), 5);

            // 4) 先构建 Canonical Schema v1 历史上下文；若附件校验/读取失败，此时还没有任何消息落库
            fc = chatAttachmentService.buildFileContext(userId, session, effectiveFileKeys, effectiveImageKeys);
            List<Map<String, Object>> extraContextItems = new ArrayList<>();
            extraContextItems.addAll(chatContextAssembler.buildClientContextItems(userId, session, req));
            extraContextItems.addAll(fc.items());
            extraContextItems.addAll(chatContextAssembler.buildPersistedAgentProjectionItems(userId, session));
            ChatContextAssembler.AgentContextBuildResult contextBuildResult = chatContextAssembler.buildAgentContextJson(
                    userId,
                    session,
                    history,
                    extraContextItems);
            fullContextJson = contextBuildResult.fullContextJson();

            SessionMessage userMsg = new SessionMessage();
            userMsg.setSessionId(session.getId());
            userMsg.setRole(SessionMessageRole.USER);
            userMsg.setType(SessionMessageType.TEXT);
            // content = AI 可读版本（用于历史回灌 fullContextJson）
            userMsg.setContent(userContent);
            // rawContent = 原始 message（保留 @pp[...] 标记，供前端渲染引用块）
            userMsg.setRawContent(userRaw);
            userMsg.setStatus(SessionMessageStatus.FINAL);
            userMsg.setDeleted(false);
            userMessageId = sessionMessageRepository.insertAutoSeq(userMsg).getId();

            for (SessionMessage attachmentMessage : preparedAttachmentWrites.messages()) {
                sessionMessageRepository.insertAutoSeq(attachmentMessage);
            }

            sessionService.touchAfterUserMessage(userId, session.getId(), content);
            // 同步刷新项目的 last_active_at
            projectService.touchProjectActivity(session.getProjectId());
            // 标题生成与 AI 回复并行：用户消息落库后立刻异步生成（不阻塞 SSE / gRPC）
            // 标题生成使用原始文本（不含注入的 fragment 内容，避免标题过长/不自然）
            final String firstUserTextForTitle = userRaw;
            try {
                chatExecutor.execute(() -> {
                    try {
                        log.debug("Auto title task scheduled (user-only): userId={}, sessionId={}", userId, session.getId());
                        autoTitleService.tryUpdateTitleAfterFirstTurn(userId, session.getId(), firstUserTextForTitle, null);
                    } catch (Exception e) {
                        log.debug("Auto title update ignored: {}", e.getMessage());
                    }
                });
            } catch (Exception e) {
                log.debug("Auto title schedule ignored: {}", e.getMessage());
            }
        } catch (RuntimeException | Error e) {
            chatExecutionLifecycle.releaseAfterPreparationFailure(executionHandle);
            throw e;
        }

        AiChatRequest aiRequest = agentRequestFactory.create(new AgentRequestFactory.RequestInput(
                userId,
                session,
                fullContextJson,
                userContent,
                executionPolicy,
                fc.imageUrls(),
                fc.fileUrls(),
                modelRoute));

        return agentStreamCoordinator.start(new AgentStreamCoordinator.StartInput(
                userId,
                session,
                executionHandle,
                aiRequest,
                requestedServiceTier,
                modelRoute.catalogId(),
                userMessageId));
    }

    private int safeLength(String value) {
        return value == null ? 0 : value.length();
    }

    private SseEmitter immediateErrorEmitter(String message) {
        SseEmitter emitter = new SseEmitter(5_000L);
        chatExecutor.execute(() -> {
            try {
                emitter.send(SseEmitter.event().name("error")
                        .data(message == null || message.isBlank() ? "请求失败" : message));
            } catch (Exception ignore) {
            } finally {
                safeComplete(emitter);
            }
        });
        return emitter;
    }

    private void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignore) {
        }
    }
}
