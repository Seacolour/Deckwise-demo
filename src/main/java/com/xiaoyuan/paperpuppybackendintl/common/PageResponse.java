package com.xiaoyuan.paperpuppybackendintl.common;

import lombok.Data;

import java.util.List;

/**
 * 通用分页返回
 *
 * @param <T> 记录类型
 */
@Data
public class PageResponse<T> {

    /**
     * 当前页号（从 1 开始）
     */
    private int current;

    /**
     * 页面大小
     */
    private int pageSize;

    /**
     * 总记录数（建议通过 COUNT(*) 获得）
     */
    private long total;

    /**
     * 是否还有下一页（基于 current/pageSize/total 推导）
     */
    private boolean hasNext;

    /**
     * 当前页数据
     */
    private List<T> records;

    public static <T> PageResponse<T> of(int current, int pageSize, long total, List<T> records) {
        PageResponse<T> r = new PageResponse<>();
        r.setCurrent(Math.max(current, 1));
        r.setPageSize(Math.max(pageSize, 1));
        r.setTotal(Math.max(total, 0));
        r.setRecords(records == null ? List.of() : records);
        long pageCount = (r.getTotal() + r.getPageSize() - 1) / r.getPageSize();
        r.setHasNext(r.getCurrent() < pageCount);
        return r;
    }
}


