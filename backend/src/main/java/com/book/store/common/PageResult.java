package com.book.store.common;

import java.util.List;

/**
 * 分页结果。list 与 total 来自同一次过滤查询（同一 Specification），
 * 保证「当前页数据」和「总条数」永远基于同一套过滤规则。
 */
public class PageResult<T> {

    public List<T> list;
    public long total;
    public int page;
    public int size;

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }
}
