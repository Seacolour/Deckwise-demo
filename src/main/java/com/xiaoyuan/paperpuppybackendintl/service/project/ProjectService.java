package com.xiaoyuan.paperpuppybackendintl.service.project;

import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectListQuery;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project Service（接口 + impl）
 */
public interface ProjectService {
    ProjectDTO createProject(Long userId, ProjectCreateRequest req);

    ProjectDTO updateProject(Long userId, ProjectUpdateRequest req);

    void deleteProject(Long userId, Long projectId);

    ProjectDTO getProject(Long userId, Long projectId);

    PageResponse<ProjectDTO> listProjects(Long userId, ProjectListQuery query);


    void touchProjectActivity(Long projectId);
}
