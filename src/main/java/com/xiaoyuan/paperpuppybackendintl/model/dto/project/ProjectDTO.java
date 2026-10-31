package com.xiaoyuan.paperpuppybackendintl.model.dto.project;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 项目对外展示 DTO
 */
@Data
public class ProjectDTO {

    private Long id;

    private Long ownerId;

    private String name;

    private String description;

    private String coverImage;

    private List<String> tags;

    private Boolean archived;

    private OffsetDateTime lastActiveAt;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

}


