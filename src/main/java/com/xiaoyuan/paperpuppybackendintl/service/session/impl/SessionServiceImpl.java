package com.xiaoyuan.paperpuppybackendintl.service.session.impl;

import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.common.PageRequest;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.session.SessionUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Project;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Session;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.SessionRepository;
import com.xiaoyuan.paperpuppybackendintl.service.session.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 会话 Service（实现类）
 */
@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final ProjectRepository projectRepository;
    private final SessionRepository sessionRepository;

    @Override
    public SessionDTO createSession(Long userId, Long projectId, SessionCreateRequest req) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目ID非法");
        }
        if (req == null || req.getTitle() == null || req.getTitle().isBlank()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话标题不能为空");
        }
        Project p = requireProjectOwner(userId, projectId);
        Session s = new Session();
        s.setProjectId(p.getId());
        s.setCreatedBy(userId);
        s.setTitle(req.getTitle());
        s.setArchived(false);
        s.setLastActiveAt(OffsetDateTime.now());
        s.setDeleted(false);
        Session saved = sessionRepository.save(s);
        return toDTO(saved);
    }

    @Override
    public PageResponse<SessionDTO> listSessions(Long userId, Long projectId, Boolean archived, PageRequest page) {
        if (projectId == null || projectId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "项目ID非法");
        }
        requireProjectOwner(userId, projectId);

        int current = page == null ? 1 : page.getCurrent();
        int pageSize = page == null ? 10 : page.getPageSize();
        if (current <= 0) current = 1;
        if (pageSize <= 0) pageSize = 10;
        pageSize = Math.min(pageSize, 200);
        int offset = (current - 1) * pageSize;

        String sortField = page == null ? null : page.getSortField();
        String sortOrder = page == null ? null : page.getSortOrder();

        List<SessionDTO> records = sessionRepository
                .listByProject(projectId, archived, offset, pageSize, sortField, sortOrder)
                .stream()
                .map(this::toDTO)
                .toList();
        int total = sessionRepository.countByProject(projectId, archived);
        return PageResponse.of(current, pageSize, total, records);
    }

    @Override
    public SessionDTO getSession(Long userId, Long sessionId) {
        Session s = requireSession(userId, sessionId);
        return toDTO(s);
    }

    @Override
    public SessionDTO updateSession(Long userId, Long sessionId, SessionUpdateRequest req) {
        Session s = requireSession(userId, sessionId);
        if (req != null) {
            if (req.getTitle() != null) {
                s.setTitle(req.getTitle());
            }
            if (req.getArchived() != null) {
                s.setArchived(req.getArchived());
            }
        }
        s.setLastActiveAt(OffsetDateTime.now());
        Session saved = sessionRepository.save(s);
        return toDTO(saved);
    }

    @Override
    public boolean deleteSession(Long userId, Long sessionId) {
        Session s = requireSession(userId, sessionId);
        return sessionRepository.softDelete(sessionId);
    }

    /**
     * 写入最后一条用户消息并刷新活跃时间（用于消息落库后的会话列表展示）
     */
    @Override
    public void touchAfterUserMessage(Long userId, Long sessionId, String lastUserMessage) {
        Session s = requireSession(userId, sessionId);
        s.setLastUserMessage(lastUserMessage);
        s.setLastActiveAt(OffsetDateTime.now());
        sessionRepository.save(s);
    }

    @Override
    public Session requireSession(Long userId, Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "会话ID非法");
        }
        Session s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在"));
        if (Boolean.TRUE.equals(s.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "会话不存在");
        }
        requireProjectOwner(userId, s.getProjectId());
        return s;
    }

    private Project requireProjectOwner(Long userId, Long projectId) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在"));
        if (Boolean.TRUE.equals(p.getDeleted())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        }
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }
        if (p.getOwnerId() == null || !p.getOwnerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限");
        }
        return p;
    }

    private SessionDTO toDTO(Session s) {
        if (s == null) {
            return null;
        }
        SessionDTO dto = new SessionDTO();
        dto.setId(s.getId());
        dto.setProjectId(s.getProjectId());
        dto.setCreatedBy(s.getCreatedBy());
        dto.setTitle(s.getTitle());
        dto.setLastUserMessage(s.getLastUserMessage());
        dto.setLastActiveAt(s.getLastActiveAt());
        dto.setArchived(s.getArchived());
        dto.setCreatedAt(s.getCreatedAt());
        dto.setUpdatedAt(s.getUpdatedAt());
        return dto;
    }
}


