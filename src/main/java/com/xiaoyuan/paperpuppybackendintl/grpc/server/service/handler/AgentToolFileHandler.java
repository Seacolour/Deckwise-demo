package com.xiaoyuan.paperpuppybackendintl.grpc.server.service.handler;

import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.FileItem;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetFileContentRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetFileContentResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetFileUrlRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetFileUrlResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetImageRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.GetImageResponse;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ListProjectFilesRequest;
import com.xiaoyuan.paperpuppybackendintl.grpc.agent.tool.ListProjectFilesResponse;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileLinkView;
import com.xiaoyuan.paperpuppybackendintl.model.dto.file.ProjectFileMappingDTO;
import com.xiaoyuan.paperpuppybackendintl.model.entity.Document;
import com.xiaoyuan.paperpuppybackendintl.model.entity.ObjectStorageFile;
import com.xiaoyuan.paperpuppybackendintl.repository.ObjectStorageFileRepository;
import com.xiaoyuan.paperpuppybackendintl.repository.ProjectFileLinkRepository;
import com.xiaoyuan.paperpuppybackendintl.service.document.DocumentService;
import com.xiaoyuan.paperpuppybackendintl.service.file.FileService;
import com.xiaoyuan.paperpuppybackendintl.service.storage.ObjectAccessUrlService;
import io.grpc.Status;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AgentToolFileHandler {

    private final FileService fileService;
    private final DocumentService documentService;
    private final ProjectFileLinkRepository projectFileLinkRepository;
    private final ObjectStorageFileRepository objectStorageFileRepository;
    private final ObjectAccessUrlService objectAccessUrlService;

    public ListProjectFilesResponse listProjectFiles(ListProjectFilesRequest request) {
        long userId = request.getUserId();
        long projectId = request.getProjectId();

        List<ProjectFileMappingDTO> files = fileService.listProjectFiles(userId, projectId);
        List<ProjectFileLinkView> views = projectFileLinkRepository.listProjectFiles(projectId);
        Map<Long, ProjectFileLinkView> viewMap = views.stream()
                .filter(v -> v.getFileId() != null)
                .collect(Collectors.toMap(ProjectFileLinkView::getFileId, v -> v, (a, b) -> a));

        List<Long> fileIds = views.stream()
                .map(ProjectFileLinkView::getFileId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<Long, Document> fileDocMap = java.util.Collections.emptyMap();
        if (!fileIds.isEmpty()) {
            List<Document> docs = documentService
                    .listByProjectAndCurrentFileIds(projectId, fileIds);
            fileDocMap = docs.stream()
                    .filter(d -> d.getCurrentFileId() != null)
                    .collect(Collectors.toMap(
                            Document::getCurrentFileId,
                            d -> d, (a, b) -> a));
        }

        ListProjectFilesResponse.Builder responseBuilder = ListProjectFilesResponse.newBuilder();
        for (ProjectFileMappingDTO dto : files) {
            FileItem.Builder itemBuilder = FileItem.newBuilder();

            ProjectFileLinkView view = dto.getId() != null ? viewMap.get(dto.getId()) : null;
            if (view != null && view.getFileId() != null) {
                itemBuilder.setFileId(view.getFileId());
            } else if (dto.getId() != null) {
                itemBuilder.setFileId(dto.getId());
            }

            if (dto.getFileName() != null) {
                itemBuilder.setFileName(dto.getFileName());
            }

            if (dto.getFileName() != null) {
                String fileName = dto.getFileName();
                int lastDot = fileName.lastIndexOf('.');
                if (lastDot > 0 && lastDot < fileName.length() - 1) {
                    String ext = fileName.substring(lastDot + 1).toLowerCase();
                    itemBuilder.setFileType(ext);
                } else if (view != null && view.getFileExt() != null) {
                    itemBuilder.setFileType(view.getFileExt().toLowerCase());
                } else {
                    itemBuilder.setFileType("unknown");
                }
            }

            if (view != null && view.getSizeBytes() != null) {
                itemBuilder.setSizeBytes(view.getSizeBytes());
            } else {
                itemBuilder.setSizeBytes(0);
            }

            if (dto.getAddedAt() != null) {
                itemBuilder.setCreatedAt(dto.getAddedAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            }
            if (dto.getDocumentId() != null) {
                itemBuilder.setDocumentId(dto.getDocumentId());
            }
            if (dto.getDocumentType() != null) {
                itemBuilder.setDocumentType(dto.getDocumentType().toDatabaseValue());
            }
            if (view != null && view.getFileId() != null) {
                Document d = fileDocMap.get(view.getFileId());
                if (d != null) {
                    if (d.getSummary() != null && !d.getSummary().isBlank()) {
                        itemBuilder.setSummary(d.getSummary());
                    }
                    if (d.getVersion() != null && d.getVersion() > 0) {
                        itemBuilder.setDocumentVersion(d.getVersion());
                    }
                    if (d.getSummaryVersion() != null && d.getSummaryVersion() > 0) {
                        itemBuilder.setSummaryVersion(d.getSummaryVersion());
                    }
                    applyDocumentScaleHints(itemBuilder, d);
                }
            }

            responseBuilder.addFiles(itemBuilder.build());
        }

        return responseBuilder.build();
    }

    private void applyDocumentScaleHints(FileItem.Builder itemBuilder, Document document) {
        if (document == null) {
            return;
        }
        if (document.getDraftContent() != null && !document.getDraftContent().isBlank()) {
            itemBuilder.setEstimatedTextChars(document.getDraftContent().length());
        }
    }



    public GetFileContentResponse getFileContent(GetFileContentRequest request) {
        long userId = request.getUserId();
        long projectId = request.getProjectId();
        long fileId = request.getFileId();
        int maxFragments = request.getMaxFragments() > 0 ? request.getMaxFragments() : 10;
        int startFragment = request.getStartFragment() > 0 ? request.getStartFragment() : 1;

        fileService.listProjectFiles(userId, projectId);

        ProjectFileLinkView fileLinkView = projectFileLinkRepository.listProjectFiles(projectId).stream()
                .filter(v -> v.getFileId() != null && v.getFileId().equals(fileId))
                .findFirst()
                .orElse(null);

        if (fileLinkView == null) {
            throw Status.NOT_FOUND
                    .withDescription("File not found in project: fileId=" + fileId + ", projectId=" + projectId)
                    .asRuntimeException();
        }

        List<String> fragments = fileService.getFileFragments(fileId, startFragment, maxFragments);
        int totalFragments = fileService.countFileFragments(fileId);
        String content = String.join("\n\n", fragments);

        GetFileContentResponse.Builder responseBuilder = GetFileContentResponse.newBuilder()
                .setFileId(fileId)
                .setContent(content)
                .setTotalFragments(totalFragments)
                .setReturnedFragments(fragments.size());

        if (fileLinkView.getDisplayName() != null) {
            responseBuilder.setFileName(fileLinkView.getDisplayName());
        }
        if (fileLinkView.getFileExt() != null) {
            responseBuilder.setFileType(fileLinkView.getFileExt().toLowerCase());
        }

        return responseBuilder.build();
    }

    public GetImageResponse getImage(GetImageRequest request) {
        long userId = request.getUserId();
        long projectId = request.getProjectId();
        long fileId = request.getFileId();

        fileService.listProjectFiles(userId, projectId);

        ProjectFileLinkView v = projectFileLinkRepository.listProjectFiles(projectId).stream()
                .filter(x -> x.getFileId() != null && x.getFileId().equals(fileId))
                .findFirst()
                .orElse(null);
        if (v == null) {
            throw Status.NOT_FOUND
                    .withDescription("File not found in project: fileId=" + fileId + ", projectId=" + projectId)
                    .asRuntimeException();
        }

        String mime = v.getMimeType();
        String ext = v.getFileExt();
        boolean isImage = (mime != null && !mime.isBlank() && mime.toLowerCase(java.util.Locale.ROOT).startsWith("image/"));
        if (!isImage && ext != null && !ext.isBlank()) {
            String e = ext.toLowerCase(java.util.Locale.ROOT).replaceFirst("^\\.", "");
            isImage = e.equals("png") || e.equals("jpg") || e.equals("jpeg") || e.equals("webp") || e.equals("gif");
        }
        if (!isImage) {
            throw Status.INVALID_ARGUMENT
                    .withDescription("GetImage only supports image/* files")
                    .asRuntimeException();
        }

        ObjectStorageFile file = requireAvailableStorageFile(fileId);
        String url = buildImageAccessUrl(file);
        if (url == null || url.isBlank()) {
            throw Status.INTERNAL
                    .withDescription("Failed to build image url for fileId=" + fileId)
                    .asRuntimeException();
        }

        GetImageResponse.Builder b = GetImageResponse.newBuilder()
                .setFileId(fileId)
                .setImageUrl(url);
        if (v.getDisplayName() != null) {
            b.setFileName(v.getDisplayName());
        }
        if (v.getFileExt() != null) {
            b.setFileType(v.getFileExt().toLowerCase());
        }
        return b.build();
    }

    public GetFileUrlResponse getFileUrl(GetFileUrlRequest request) {
        long userId = request.getUserId();
        long projectId = request.getProjectId();
        long fileId = request.getFileId();

        fileService.listProjectFiles(userId, projectId);

        ProjectFileLinkView v = projectFileLinkRepository.listProjectFiles(projectId).stream()
                .filter(x -> x.getFileId() != null && x.getFileId().equals(fileId))
                .findFirst()
                .orElse(null);
        if (v == null) {
            throw Status.NOT_FOUND
                    .withDescription("File not found in project: fileId=" + fileId + ", projectId=" + projectId)
                    .asRuntimeException();
        }

        ObjectStorageFile file = requireAvailableStorageFile(fileId);
        String url = buildFileAccessUrl(file);
        if (url == null || url.isBlank()) {
            throw Status.FAILED_PRECONDITION
                    .withDescription("File has no AI-readable URL: fileId=" + fileId)
                    .asRuntimeException();
        }

        GetFileUrlResponse.Builder b = GetFileUrlResponse.newBuilder()
                .setFileId(fileId)
                .setFileUrl(url);
        if (v.getDisplayName() != null) {
            b.setFileName(v.getDisplayName());
        }
        if (v.getFileExt() != null) {
            b.setFileType(v.getFileExt().toLowerCase());
        }
        if (v.getSizeBytes() != null) {
            b.setSizeBytes(v.getSizeBytes());
        }
        return b.build();
    }

    private ObjectStorageFile requireAvailableStorageFile(long fileId) {
        ObjectStorageFile file = objectStorageFileRepository.findById(fileId)
                .orElseThrow(() -> Status.NOT_FOUND
                        .withDescription("File storage record not found: fileId=" + fileId)
                        .asRuntimeException());
        if (Boolean.TRUE.equals(file.getDeleted())
                || file.getStatus() == null
                || !"available".equalsIgnoreCase(file.getStatus())) {
            throw Status.NOT_FOUND
                    .withDescription("File storage record is not available: fileId=" + fileId)
                    .asRuntimeException();
        }
        return file;
    }

    private String buildFileAccessUrl(ObjectStorageFile file) {
        String url = objectAccessUrlService.privateFileUrl(file);
        if (url != null && !url.isBlank()) {
            return url;
        }
        return objectAccessUrlService.publicUrl(file);
    }

    private String buildImageAccessUrl(ObjectStorageFile file) {
        String url = objectAccessUrlService.privateImageUrl(file, Map.of());
        if (url != null && !url.isBlank()) {
            return url;
        }
        return objectAccessUrlService.publicUrl(file);
    }
}
