package com.xiaoyuan.paperpuppybackendintl.service.file;

import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FileUploadCacheResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FilePurgePreviewResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FilePurgeRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.FilePurgeResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileMappingDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.RecentProjectFileDTO;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface FileService {

        FileUploadCacheResponse upload(Long userId, MultipartFile file, Long projectId);

        /**
         * 上传文件，可选择是否在 PDF 上传成功后异步触发图片派生解析。
         */
        FileUploadCacheResponse upload(Long userId, MultipartFile file, Long projectId, boolean extractPdfImages);

        /**
         * 上传文件，可由前端标记来源意图：用户主动上传走可复用 bytes，生成产物走 generated。
         */
        FileUploadCacheResponse upload(Long userId, MultipartFile file, Long projectId,
                        boolean extractPdfImages, @Nullable String uploadSource);

        /**
         * 上传项目图片资源。传入 private storage 请求参数时写入 private storage，
         * 并返回稳定对象元数据和当次可访问 URL。
         */
        FileUploadCacheResponse uploadProjectImage(Long userId, MultipartFile file, Long projectId,
                        @Nullable String uploadSource, @Nullable String storageChannel, @Nullable String visibility);

        /**
         * 上传文件（复用外部已读取的 bytes，避免重复读取 MultipartFile）。
         * <p>
         * 典型场景：上传到项目后立即触发 JRAG 向量化，需要同一份 bytes 既用于上传 R2，也用于 gRPC 直传。
         */
        FileUploadCacheResponse uploadWithBytes(Long userId, MultipartFile file, Long projectId,
                        byte[] bytes);

        /**
         * 上传文件（复用外部已读取的 bytes），可选择是否异步触发 PDF 图片派生解析。
         */
        FileUploadCacheResponse uploadWithBytes(Long userId, MultipartFile file, Long projectId,
                        byte[] bytes, boolean extractPdfImages);

        /**
         * 上传文件（复用外部已读取的 bytes），并显式标记来源意图。
         */
        FileUploadCacheResponse uploadWithBytes(Long userId, MultipartFile file, Long projectId,
                        byte[] bytes, boolean extractPdfImages, @Nullable String uploadSource);

        /**
         * 存储系统/AI 生成文件。生成产物拥有独立 source_key，不参与用户上传内容去重。
         */
        FileUploadCacheResponse uploadGeneratedWithBytes(Long userId, MultipartFile file, Long projectId,
                        byte[] bytes);

        /**
         * 存储系统/AI 生成的私有文件。仅供后端业务内部调用，不暴露为前端上传开关。
         */
        FileUploadCacheResponse uploadGeneratedPrivateWithBytes(Long userId, MultipartFile file, Long projectId,
                        byte[] bytes);

        /**
         * 文档发布（publish）专用上传：将 draft 内容落为独立的发布产物文件，不走上传去重，也不触发"上传 Markdown 自动创建 Document"逻辑。
         * <p>
         * 每次发布都会创建独立的 object_storage_files 记录（source_type='publish'），
         * source_key 包含 documentId + versionNo + bytesHash，确保不同文档/不同版本的发布永远不会共享同一个 file_id。
         * </p>
         *
         * @param userId     用户ID
         * @param file       内存文件（通常为 markdown / xlsx）
         * @param projectId  项目ID（必须）
         * @param bytes      文件内容
         * @param documentId 文档ID（用于生成唯一 source_key）
         * @param versionNo  即将写入的版本号（用于生成唯一 source_key）
         */
        FileUploadCacheResponse uploadWithBytesForDocumentPublish(Long userId, MultipartFile file, Long projectId,
                        byte[] bytes, Long documentId, int versionNo);

        boolean addFileToProject(Long userId, Long projectId, String cacheKey, String fileName, @Nullable Long fileId);

        /**
         * 在同一项目内复制一份独立文件身份。
         * <p>
         * 复制后的文件会拥有新的 object_storage_files 记录和新的 source_key，
         * 后续可独立编辑/发布，不会退化为“只改展示名”的复用。
         */
        FileUploadCacheResponse duplicateProjectFile(Long userId, Long projectId, Long fileId, @Nullable String newName);

        boolean removeFileFromProject(Long userId, Long projectId, String cacheKey);

        FilePurgePreviewResponse previewProjectFilePurge(Long userId, Long projectId, Long fileId);

        FilePurgeResponse purgeProjectFile(Long userId, Long projectId, Long fileId, FilePurgeRequest request);

        boolean renameProjectFile(Long userId, Long projectId, Long fileId, String newName);

        List<ProjectFileMappingDTO> listProjectFiles(Long userId, Long projectId);

        List<RecentProjectFileDTO> listRecentUserUploadedFiles(Long userId, int limit);

        /**
         * 获取文件切片内容（用于 AI 工具读取 PDF 等文件内容）
         *
         * @param fileId       文件 ID
         * @param maxFragments 最大返回切片数
         * @return 切片内容列表
         */
        List<String> getFileFragments(Long fileId, int maxFragments);

        /**
         * 获取文件切片窗口内容（startFragment 为 1-based）。
         *
         * @param fileId         文件 ID
         * @param startFragment  起始切片（1-based，包含起点）
         * @param maxFragments   最大返回切片数
         * @return 切片内容列表
         */
        List<String> getFileFragments(Long fileId, int startFragment, int maxFragments);

        /**
         * 统计文件切片总数
         */
        int countFileFragments(Long fileId);
}
