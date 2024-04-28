package com.processmining.logdeploy.autodeploy.dao;

import com.processmining.logdeploy.autodeploy.entity.Collect;
import com.processmining.logdeploy.autodeploy.entity.Project;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectDao {

    int addProject(@Param("project") Project project);
    List<Project> getAllProject(@Param("user_id") Long user_id);
    List<Collect> getCollect(@Param("project_id") Long project_id);
    // delete Project
    int deleteProjectByID(@Param("id") Long id);
    int deleteCollectByProjectID(@Param("id") Long id);
    // delete Collect
    int deleteCollectByID(@Param("id") Long id);

}
