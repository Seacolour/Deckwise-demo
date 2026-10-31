package com.xiaoyuan.paperpuppybackendintl.service.toolbook.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolBookServiceImplTest {

    private ToolBookServiceImpl toolBookService;

    @BeforeEach
    void setUp() {
        toolBookService = new ToolBookServiceImpl(new DefaultResourceLoader(), new ObjectMapper());
        ReflectionTestUtils.setField(toolBookService, "manifestPattern", "classpath*:tool-books/*/manifest.json");
        ReflectionTestUtils.setField(toolBookService, "defaultNodeMaxChars", 12_000);
    }

    @Test
    void listBooksIsEmptyAfterRetiringSpecializedBooks() {
        var books = toolBookService.listBooks();

        assertTrue(books.isEmpty());
    }



}
