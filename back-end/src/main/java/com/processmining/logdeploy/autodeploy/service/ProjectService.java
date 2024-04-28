package com.processmining.logdeploy.autodeploy.service;

import com.processmining.logdeploy.autodeploy.entity.Project;
import com.processmining.logdeploy.autodeploy.common.lang.Result;

import java.util.List;

public interface ProjectService {

    Result addProject(Project project);
    void getAllProject_Service(Long user_id, List<Object> data);
    Result getAllProject(Long user_id);
    Result deleteProject(Long id);
    Result deleteCollect(Long id);
    Result searchProjectAndCollect(Long user_id, String queryText);

}
