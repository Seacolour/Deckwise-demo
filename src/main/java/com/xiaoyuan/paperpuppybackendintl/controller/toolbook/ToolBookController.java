package com.xiaoyuan.paperpuppybackendintl.controller.toolbook;

import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookNodeContentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookSummaryDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookTocDTO;
import com.xiaoyuan.paperpuppybackendintl.service.toolbook.ToolBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/tool-books")
@AuthCheck
@RequiredArgsConstructor
public class ToolBookController {

    private final ToolBookService toolBookService;

    @GetMapping
    public BaseResponse<List<ToolBookSummaryDTO>> listBooks() {
        return ResultUtils.success(toolBookService.listBooks());
    }

    @GetMapping("/{bookKey}")
    public BaseResponse<ToolBookTocDTO> getBookToc(@PathVariable String bookKey) {
        return ResultUtils.success(toolBookService.getBookToc(bookKey));
    }

    @GetMapping("/{bookKey}/nodes/{nodeKey}")
    public BaseResponse<ToolBookNodeContentDTO> readNode(
            @PathVariable String bookKey,
            @PathVariable String nodeKey,
            @RequestParam(value = "maxChars", required = false) Integer maxChars) {
        return ResultUtils.success(toolBookService.readNode(bookKey, nodeKey, maxChars));
    }
}
