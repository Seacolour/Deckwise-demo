package com.xiaoyuan.paperpuppybackendintl.model.dto.project;

import com.xiaoyuan.paperpuppybackendintl.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 列表查询项目的过滤条件
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectListQuery extends PageRequest {

    /**
     * 是否归档，null 表示全部
     */
    private Boolean archived;
}


