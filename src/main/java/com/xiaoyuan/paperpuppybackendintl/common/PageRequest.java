package com.xiaoyuan.paperpuppybackendintl.common;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 分页请求
 */
@Data
public class PageRequest {

    /**
     * 当前页号
     */
    @Min(value = 1, message = "current 必须 >= 1")
    private int current = 1;

    /**
     * 页面大小
     */
    @Min(value = 1, message = "pageSize 必须 >= 1")
    @Max(value = 200, message = "pageSize 最大为 200")
    private int pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方向：asc / desc
     */
    @Pattern(
            regexp = "^(asc|desc|ASC|DESC)$",
            message = "sortOrder 仅允许 asc/desc"
    )
    private String sortOrder = "asc";
}
