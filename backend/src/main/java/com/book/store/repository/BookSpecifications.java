package com.book.store.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.book.store.entity.Book;

import jakarta.persistence.criteria.Predicate;

/**
 * 图书查询过滤规则的唯一出处：图书列表（不分页）、分页查询、以及前端详情跳转前的
 * 结果定位全部使用这里构造的 Specification，保证「列表看到的」与「详情打开的」
 * 判定口径完全一致，刷新或返回列表后结果不会漂移。
 *
 * <p>规则口径：</p>
 * <ul>
 *   <li>status：精确匹配；</li>
 *   <li>keyword：书名 / 作者 / ISBN 任一包含（转义 % _ \，通配符不生效）；</li>
 *   <li>isbn：完整或部分 ISBN 的包含匹配。空白一律视为「未指定该条件」，
 *       绝不会被当成任意匹配；isbn 为 NULL 的旧数据天然不会被 LIKE 命中。</li>
 * </ul>
 */
public final class BookSpecifications {

    private BookSpecifications() {
    }

    public static Specification<Book> of(String status, String keyword, String isbn) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                ps.add(cb.equal(root.get("status"), status.trim()));
            }
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + escapeLike(keyword.trim()) + "%";
                ps.add(cb.or(
                        cb.like(root.get("title"), like, '\\'),
                        cb.like(root.get("author"), like, '\\'),
                        cb.like(root.get("isbn"), like, '\\')));
            }
            if (isbn != null && !isbn.isBlank()) {
                ps.add(cb.like(root.get("isbn"), "%" + escapeLike(isbn.trim()) + "%", '\\'));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }

    /** LIKE 通配符转义：用户输入的 % _ \ 只按字面匹配，不能当通配符用 */
    private static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
