package com.xiaoyuan.paperpuppybackendintl.service.file.jgda;

import java.io.IOException;

public interface JgdaPdfAssetClient {

    JgdaPdfAssetExtractionResult extract(String fileName, byte[] pdfBytes) throws IOException;
}
