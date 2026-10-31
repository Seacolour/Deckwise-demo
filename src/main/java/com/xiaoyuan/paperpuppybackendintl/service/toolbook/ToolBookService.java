package com.xiaoyuan.paperpuppybackendintl.service.toolbook;

import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookNodeContentDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookSummaryDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.toolbook.ToolBookTocDTO;

import java.util.List;

public interface ToolBookService {

    List<ToolBookSummaryDTO> listBooks();

    ToolBookTocDTO getBookToc(String bookKey);

    ToolBookNodeContentDTO readNode(String bookKey, String nodeKey, Integer maxChars);
}
