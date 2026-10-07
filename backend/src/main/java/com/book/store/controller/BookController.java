package com.book.store.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.book.store.common.ApiResponse;
import com.book.store.entity.Book;
import com.book.store.service.BookService;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    /**
     * 图书查询。status / keyword / isbn 三个过滤条件与分页参数自由组合，
     * 全部走同一套过滤规则（BookSpecifications）：
     * <ul>
     *   <li>不带 page/size：返回完整列表（导航计数、下拉选择等旧调用方不变）；</li>
     *   <li>带 page 或 size：返回分页结果 {list, total, page, size}。</li>
     * </ul>
     */
    @GetMapping
    public ApiResponse<?> list(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String isbn,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size) {
        if (page == null && size == null) {
            return ApiResponse.success(bookService.list(status, keyword, isbn));
        }
        return ApiResponse.success(bookService.page(status, keyword, isbn, page, size));
    }

    /**
     * 图书详情：只按主键定位，与列表排序 / 分页无关。
     */
    @GetMapping("/{id}")
    public ApiResponse<Book> detail(@PathVariable Long id) {
        return ApiResponse.success(bookService.get(id));
    }

    @PostMapping
    public ApiResponse<Book> create(@RequestBody Book book) {
        return ApiResponse.success(bookService.create(book));
    }

    @PutMapping("/{id}")
    public ApiResponse<Book> update(@PathVariable Long id, @RequestBody Book book) {
        return ApiResponse.success(bookService.update(id, book));
    }
}
