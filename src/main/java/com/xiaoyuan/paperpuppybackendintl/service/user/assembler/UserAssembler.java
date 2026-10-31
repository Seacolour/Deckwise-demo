package com.xiaoyuan.paperpuppybackendintl.service.user.assembler;

import com.xiaoyuan.paperpuppybackendintl.model.dto.user.UserDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.User;
import org.springframework.stereotype.Component;

/**
 * User DTO 与实体之间的转换器
 */
@Component
public class UserAssembler {

    public UserDTO toDTO(User user) {
        if (user == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setEmailVerified(user.getEmailVerified());
        dto.setName(user.getName());
        dto.setSignUpProvider(user.getSignUpProvider());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setRole(user.getRole() != null ? user.getRole().name() : null);
        return dto;
    }

    /**
     * 转换为公开 DTO（不含 email、role 等敏感字段），用于非本人查询场景
     */
    public UserDTO toPublicDTO(User user) {
        if (user == null) {
            return null;
        }
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        return dto;
    }
}


