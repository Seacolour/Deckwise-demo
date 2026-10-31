package com.xiaoyuan.paperpuppybackendintl.model.dto.project;

import lombok.Data;

import java.util.List;

/**
 * 创建项目请求体
 */
@Data
public class ProjectCreateRequest {

    private String name;

    private String description;

    private String coverImage;

    private List<String> tags;
}


