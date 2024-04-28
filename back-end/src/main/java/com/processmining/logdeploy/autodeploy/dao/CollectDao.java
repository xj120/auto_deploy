package com.processmining.logdeploy.autodeploy.dao;

import com.processmining.logdeploy.autodeploy.entity.*;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CollectDao {

    List<Server> getServerByProjectId(@Param("project_id") Long project_id);
    List<Cluster> getClusterByProjectId(@Param("project_id") Long project_id);

    Server getServerById(@Param("id") Long id);
    String getUserName(@Param("project_id") Long project_id);

    int addServerDeploy(@Param("deploy")Deploy deploy);

    List<Deploy> getDeploy(@Param("project_id") Long project_id);
    Cluster getClusterById(@Param("id") Long id);

    int deleteDeployById(@Param("id") Long id);

    Deploy getDeployById(@Param("id") Long id);

    int addCollect(@Param("collect") Collect collect);
    int addCollectPackage(@Param("collectPackage") CollectPackage collectPackage);
    List<String> getCollectPackage(@Param("project_id") Long project_id, @Param("collect_id") Long collect_id);

    List<Collect> getCollect(@Param("project_id") Long project_id);

    Collect getCollectById(@Param("id") Long id);

    int updateIsBuild(@Param("id") Long id, @Param("is_build") Boolean is_build);

    String getProjectName(@Param("id") Long id);

    List<Server> getServer(@Param("project_id") Long project_id);
    int addCluster(@Param("cluster") Cluster cluster);
    int addClusterServer(@Param("cluster_id") Long cluster_id, @Param("server_id") Long server_id);

    Cluster getCluster(@Param("cluster_id") Long cluster_id);
    List<Server> getServerList(@Param("cluster_id") Long cluster_id);
    int addClusterDeploy(@Param("deploy") Deploy deploy);

}
