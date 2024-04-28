package com.processmining.logdeploy.autodeploy.dao;

import com.processmining.logdeploy.autodeploy.entity.CollectTool;
import com.processmining.logdeploy.autodeploy.entity.Server;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServerDao {

    int addServer(@Param("server") Server server);
    String getUserName(@Param("server") Server server);
    int addCollectTool(@Param("collectTool") CollectTool collectTool);

    List<Server> getServer(@Param("project_id") Long project_id);     //错误 sql语言重复
    CollectTool getCollectToolByServerId(@Param("server_id") Long server_id);

    int updateStatus(@Param("server") Server server);

    int deleteServer(@Param("server") Server server);
    int deleteServerById(@Param("id") Long id);

}
