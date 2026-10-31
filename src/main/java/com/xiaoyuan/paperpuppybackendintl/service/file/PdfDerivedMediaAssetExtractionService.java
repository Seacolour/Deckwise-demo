package com.xiaoyuan.paperpuppybackendintl.service.file;

import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;


public interface PdfDerivedMediaAssetExtractionService {

    boolean requestExtractionAfterCommit(Long userId,
                                         Long projectId,
                                         ObjectStorageFile sourceFile,
                                         byte[] pdfBytes,
                                         String fileName);

    boolean requestDocxExtractionAfterCommit(Long userId,
                                             Long projectId,
                                             ObjectStorageFile sourceFile,
                                             byte[] docxBytes,
                                             String fileName);

    boolean requestPptxExtractionAfterCommit(Long userId,
                                             Long projectId,
                                             ObjectStorageFile sourceFile,
                                             byte[] pptxBytes,
                                             String fileName);

}
