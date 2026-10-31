package com.xiaoyuan.paperpuppybackendintl.controller.project;

import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectCreateRequest;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectDTO;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectListQuery;
import com.xiaoyuan.paperpuppybackendintl.model.dto.project.ProjectUpdateRequest;
import com.xiaoyuan.paperpuppybackendintl.common.BaseResponse;
import com.xiaoyuan.paperpuppybackendintl.common.PageResponse;
import com.xiaoyuan.paperpuppybackendintl.common.ResultUtils;
import com.xiaoyuan.paperpuppybackendintl.annotation.AuthCheck;
import com.xiaoyuan.paperpuppybackendintl.annotation.CurrentUserId;
import com.xiaoyuan.paperpuppybackendintl.service.project.ProjectService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;

/**
 * 项目（工作空间）接口
 */
@RestController
@RequestMapping("/projects")
@AuthCheck
@Validated
public class ProjectController {

    private final ProjectService projectService;
    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /**
     * 创建项目
     *
     * @param req
     * @return
     */
    @PostMapping("/create")
    public BaseResponse<ProjectDTO> createProject(@RequestBody ProjectCreateRequest req,
                                                  @CurrentUserId Long userId) {
        return ResultUtils.success(projectService.createProject(userId, req));
    }

    /**
     * 更新项目
     *
     * @param projectId
     * @param req
     * @return
     */
    @PutMapping("/{projectId}")
    public BaseResponse<ProjectDTO> updateProject(@PathVariable Long projectId,
                                                  @RequestBody ProjectUpdateRequest req,
                                                  @Parameter(hidden = true)
                                                  @CurrentUserId Long userId) {
        req.setId(projectId);
        return ResultUtils.success(projectService.updateProject(userId, req));
    }

    /**
     * 删除项目
     *
     * @param projectId
     * @return
     */
    @DeleteMapping("/{projectId}")
    public BaseResponse<Boolean> deleteProject(@PathVariable Long projectId,
                                               @CurrentUserId Long userId) {
        projectService.deleteProject(userId, projectId);
        return ResultUtils.success(true);
    }

    /**
     * 获取项目详情
     *
     * @param projectId
     * @return
     */
    @GetMapping("/{projectId}")
    public BaseResponse<ProjectDTO> getProject(@PathVariable Long projectId,
                                               @CurrentUserId Long userId) {
        return ResultUtils.success(projectService.getProject(userId, projectId));
    }

    /**
     * 当前用户的项目
     *
     * @param query
     * @return
     */
    @PostMapping("/list")
    public BaseResponse<PageResponse<ProjectDTO>> listProjects(@Valid @RequestBody ProjectListQuery query,
                                                               @Parameter(hidden = true)
                                                               @CurrentUserId Long userId) {
        return ResultUtils.success(projectService.listProjects(userId, query));
    }

}


