package com.processmining.logdeploy.autodeploy.service;

import com.processmining.logdeploy.autodeploy.common.lang.Result;
import com.processmining.logdeploy.autodeploy.entity.Server;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ServerService {

    Result addServer(MultipartFile file, Server server);
    Result addServerWithoutFile(Server server);
    Result getServer(Long currentPage, Long pageSize, Long project_id);
    Result testServer(Server server);
    Result deleteServer(Server server);
    Result deleteServerBatch(List<Map<String, Object>> mapList);
    Result queryServer(String selectLabel, String queryInfo, Long currentPage, Long pageSize, Long project_id);

}
