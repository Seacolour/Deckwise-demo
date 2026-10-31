package com.xiaoyuan.paperpuppybackendintl.model.dto.project;

import lombok.Data;

import java.util.List;

/**
 * 更新项目请求体
 */
@Data
public class ProjectUpdateRequest {

    private Long id;

    private String name;

    private String description;

    private String coverImage;

    private List<String> tags;

    private Boolean archived;

}


