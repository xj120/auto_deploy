package com.processmining.logdeploy.autodeploy.service.impl;

import com.processmining.logdeploy.autodeploy.entity.Collect;
import com.processmining.logdeploy.autodeploy.entity.Project;
import com.processmining.logdeploy.autodeploy.common.lang.Result;
import com.processmining.logdeploy.autodeploy.dao.ProjectDao;
import com.processmining.logdeploy.autodeploy.service.ProjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProjectServiceImpl{
    @Autowired
    private ProjectDao projectDao;
    public void getAllProject_Service(Long user_id, List<Object> data) {
        List<Project> projects = projectDao.getAllProject(user_id);
        for (int i = 0; i < projects.size(); i++) {
            Project project = projects.get(i);
            List<Collect> collects = projectDao.getCollect(project.getId());
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("project", project);
            dataMap.put("collects", collects);
            data.add(dataMap);
        }
    }

    public Result addProject(Project project) {
        int code = projectDao.addProject(project);
        String msg = code > 0 ? "项目创建成功" : "项目创建失败";
        code = code > 0 ? 200 : 400;
        return Result.success(code, msg, null);
    }





    public Result getAllProject(Long user_id) {   //数据流分析1
        List<Object> data = new ArrayList<>();
        List<Project> projects = projectDao.getAllProject(user_id);
        for (int i = 0; i < projects.size(); i++) {     //遇到循环语句for,while 一起转移
            Project project = projects.get(i);
            List<Collect> collects = projectDao.getCollect(project.getId());
            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("project", project);
            dataMap.put("collects", collects);
            data.add(dataMap);
        }
        return Result.success(200, "获取项目列表成功", data);
    }


    public Result deleteProject(Long id) {
        int isDeleteCollects = projectDao.deleteCollectByProjectID(id);
        int isDeleteProject = projectDao.deleteProjectByID(id);
        int code = 200;
        String msg = "删除成功";
        if(isDeleteCollects < 0 || isDeleteProject < 0) {
            code = 400;
            msg = "删除失败";
        }
        return Result.success(code, msg, null);
    }


    public Result deleteCollect(Long id) {
        int code = projectDao.deleteCollectByID(id);
        String msg = code > 0 ? "删除成功" : "删除失败";
        code = code > 0 ? 200 : 400;
        return Result.success(code, msg, null);
    }


    public Result searchProjectAndCollect(Long user_id, String queryText) {
        Map<String, Object> response = new HashMap<>();
        // data & state
        List<Object> data = new ArrayList<>();
        Map<Long, String> state = new HashMap<>();

        List<Project> projects = projectDao.getAllProject(user_id);
        for (int i = 0; i < projects.size(); i++) {
            Project project = projects.get(i);
            List<Collect> collects = projectDao.getCollect(project.getId());
            // 1. projectName contains queryText
            // 注: 优先搜索项目, 即若项目名包含 queryText, 该项目下不包含 queryText 的采集任务也显示
            if (project.getName().contains(queryText)) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("project", project);
                dataMap.put("collects", collects);
                data.add(dataMap);
                state.put(project.getId(), "close");
            }
            else {
                List<Collect> containsQueryTextCollects = new ArrayList<>();
                for (int j = 0; j < collects.size(); j++) {
                    Collect collect = collects.get(j);
                    if (collect.getName().contains(queryText)) {
                        containsQueryTextCollects.add(collect);
                    }
                }
                // 2. projectName don't contain queryText but collect contain queryText
                if (!containsQueryTextCollects.isEmpty()) {
                    Map<String, Object> dataMap = new HashMap<>();
                    dataMap.put("project", project);
                    dataMap.put("collects", containsQueryTextCollects);
                    data.add(dataMap);
                    state.put(project.getId(), "open");
                }
            }
        }
        response.put("data", data);
        response.put("state", state);

        return Result.success(200, "查询成功", response);
    }

}
