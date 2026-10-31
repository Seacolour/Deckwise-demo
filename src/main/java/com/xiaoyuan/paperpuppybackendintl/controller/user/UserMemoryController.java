package com.xiaoyuan.paperpuppybackendintl.controller.user;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ErrorCode;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.exception.BusinessException;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.CreateUserMemoryRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UpdateUserMemoryRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserMemoryDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.UserMemory;
import com.xiaoyuan.paperpuppybackendintl.repository.UserMemoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/memories")
@RequiredArgsConstructor
@AuthCheck
public class UserMemoryController {

    private static final int MAX_MEMORIES_PER_USER = 200;

    private final UserMemoryRepository userMemoryRepository;

    @GetMapping
    public BaseResponse<List<UserMemoryDTO>> listMemories(@CurrentUserId Long userId) {
        List<UserMemoryDTO> list = userMemoryRepository.listByUserId(userId)
                .stream().map(this::toDTO).toList();
        return ResultUtils.success(list);
    }

    @PostMapping
    public BaseResponse<UserMemoryDTO> createMemory(@CurrentUserId Long userId,
                                                    @Validated @RequestBody CreateUserMemoryRequest req) {
        int count = userMemoryRepository.countByUserId(userId);
        if (count >= MAX_MEMORIES_PER_USER) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "记忆数量已达上限（" + MAX_MEMORIES_PER_USER + "条）");
        }
        UserMemory memory = new UserMemory();
        memory.setUserId(userId);
        memory.setTitle(req.getTitle());
        memory.setContent(req.getContent());
        memory.setSource("user");
        UserMemory saved = userMemoryRepository.insert(memory);
        return ResultUtils.success(toDTO(saved));
    }

    @PutMapping("/{memoryId}")
    public BaseResponse<Boolean> updateMemory(@CurrentUserId Long userId,
                                              @PathVariable Long memoryId,
                                              @Validated @RequestBody UpdateUserMemoryRequest req) {
        UserMemory memory = userMemoryRepository.findById(memoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记忆不存在"));
        if (!memory.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此记忆");
        }
        boolean ok = userMemoryRepository.updateContent(memoryId, req.getTitle(), req.getContent());
        return ResultUtils.success(ok);
    }

    @DeleteMapping("/{memoryId}")
    public BaseResponse<Boolean> deleteMemory(@CurrentUserId Long userId,
                                              @PathVariable Long memoryId) {
        UserMemory memory = userMemoryRepository.findById(memoryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "记忆不存在"));
        if (!memory.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作此记忆");
        }
        boolean ok = userMemoryRepository.softDelete(memoryId);
        return ResultUtils.success(ok);
    }

    private UserMemoryDTO toDTO(UserMemory memory) {
        UserMemoryDTO dto = new UserMemoryDTO();
        dto.setId(memory.getId());
        dto.setTitle(memory.getTitle());
        dto.setContent(memory.getContent());
        dto.setSource(memory.getSource());
        dto.setCreatedAt(memory.getCreatedAt());
        dto.setUpdatedAt(memory.getUpdatedAt());
        return dto;
    }
}
