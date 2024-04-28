package com.processmining.logdeploy.autodeploy.service.impl;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.processmining.logdeploy.autodeploy.common.lang.Result;
import com.processmining.logdeploy.autodeploy.controller.UserController;
import com.processmining.logdeploy.autodeploy.dao.ServerDao;
import com.processmining.logdeploy.autodeploy.entity.CollectTool;
import com.processmining.logdeploy.autodeploy.entity.Path;
import com.processmining.logdeploy.autodeploy.entity.Server;
import com.processmining.logdeploy.autodeploy.service.ServerService;
import com.processmining.logdeploy.autodeploy.util.JSchUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class ServerServiceImpl implements ServerService{
    @Autowired
    private ServerDao serverDao;
    @Autowired
    private UserController userController;

    public List<String> getFileDirectories(File file, List<String> directories) {
        File[] files = file.listFiles();
        if (files == null) {
            return directories;
        }
        for (File _file: files) {
            if (_file.isDirectory()) {
                directories.add(_file.getAbsolutePath());
                getFileDirectories(_file, directories);
            }
        }
        return directories;
    }


    public Result addServer(MultipartFile file, Server server) {
        String localToolPath = Path.LOCAL_TOOL(serverDao.getUserName(server));

        // save CollectTool to local project
        boolean isSave = userController.saveFile(file, localToolPath);

        // upload CollectTool to remote server
        Server.Status status = server.testConnectivity();
        String remoteToolPath = Path.REMOTE_TOOL(server.getUsername());
        if (status.equals(Server.Status.CONNECTED)) {
            try {
                Session session = server.sshSession();
                session.connect(Server.CONNECT_TIMEOUT);
                JSchUtils.remoteExecute(session, "mkdir -p " + remoteToolPath);

                File dirFile = new File(localToolPath);
                if (dirFile.exists() && dirFile.isDirectory()) {
                    File[] files = dirFile.listFiles();
                    if (files != null) {
                        for (File _file : files) {
                            if (_file.isFile()) {
                                try {
                                    JSchUtils.upload(session, _file.getAbsolutePath(), remoteToolPath);
                                } catch (JSchException | SftpException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }

                File logServer = new File(Path.DEFAULT_SERVER());
                JSchUtils.upload(session, logServer.getAbsolutePath(), remoteToolPath);

                if (session.isConnected())
                    session.disconnect();

            } catch (JSchException | SftpException e) {
                e.printStackTrace();
            }
        }

        // add Server to database
        server.setCreated(LocalDateTime.now());
        int isAddServer = serverDao.addServer(server);

        // add CollectTool to database
        CollectTool collectTool = new CollectTool();
        collectTool.setServer_id(server.getId());
        collectTool.setName(file.getOriginalFilename());
        collectTool.setDirectory(remoteToolPath);
        int isAddCollectTool = serverDao.addCollectTool(collectTool);

        if((isAddServer > 0) && isSave && (isAddCollectTool > 0))
            return Result.success(200, "成功添加服务器", null);
        else
            return Result.fail("添加服务器失败");
    }


    @Override
    public Result addServerWithoutFile(Server server) {
        // upload CollectTool to remote server
        Server.Status status = server.testConnectivity();
        String remoteToolPath = Path.REMOTE_TOOL(server.getUsername());
        if (status.equals(Server.Status.CONNECTED)) {
            try {
                Session session = server.sshSession();
                session.connect(Server.CONNECT_TIMEOUT);
                JSchUtils.remoteExecute(session, "mkdir -p " + remoteToolPath);

                File logServer = new File(Path.DEFAULT_SERVER());
                JSchUtils.upload(session, logServer.getAbsolutePath(), remoteToolPath);

                File logAgent = new File(Path.DEFAULT_AGENT());
                JSchUtils.upload(session, logAgent.getAbsolutePath(), remoteToolPath);

                if (session.isConnected())
                    session.disconnect();

            } catch (JSchException | SftpException e) {
                e.printStackTrace();
            }
        }

        // add Server to database
        server.setCreated(LocalDateTime.now());
        int isAddServer = serverDao.addServer(server);

        // add default collectTool to database
        CollectTool collectTool = new CollectTool();
        collectTool.setServer_id(server.getId());
        collectTool.setName("log_agent-0.0.3-SNAPSHOT.jar");
        collectTool.setDirectory(remoteToolPath);
        int isAddCollectTool = serverDao.addCollectTool(collectTool);

        if ((isAddServer > 0) && (isAddCollectTool > 0))
            return Result.success(200, "成功添加服务器", null);
        else
            return Result.fail("添加服务器失败");
    }

 
    @Override
    public Result getServer(Long currentPage, Long pageSize, Long project_id) {  //局部功能设计标记点
        List<Object> data = new ArrayList<>();
        // get serverAmount
        int serverAmount = serverDao.getServer(project_id).size();

        // get serverList
        int begin = pageSize.intValue() * (currentPage.intValue() - 1);
        int end = Math.min(pageSize.intValue() * currentPage.intValue(), serverAmount);
        List<Server> serverList = serverDao.getServer(project_id).subList(begin, end);

        // get collectToolName and put (serverAmount, server, collectToolName) to dataItem(Map)
        for (Server server : serverList) {
            Map<String, Object> dataItem = new HashMap<>();
            dataItem.put("serverAmount", serverAmount);
            dataItem.put("server", server);
            dataItem.put("collectToolName", serverDao.getCollectToolByServerId(server.getId()).getName());
            data.add(dataItem);
        }

        return Result.success(200, "获取服务器列表成功", data);
    }

    @Override
    public Result testServer(Server server) {
        Server.Status status = server.testConnectivity();
        String msg = status.equals(Server.Status.CONNECTED) ? "连接测试成功" : "连接测试失败";

        if (server.getId() != null && server.getId() > 0)
            serverDao.updateStatus(server);

        return Result.success(200, msg, status);
    }

    @Override
    public Result deleteServer(Server server) {
        int isDelete = serverDao.deleteServer(server);
        if (isDelete > 0)
            return Result.success(200, "success", null);
        else
            return Result.fail("fail");
    }

    @Override
    public Result deleteServerBatch(List<Map<String, Object>> mapList) {
        if (mapList.size() == 0) {
            return Result.success(200, "success", null);
        }
        else {
            for (Map<String, Object> serverMap: mapList) {
                Map<String, Object> server = (Map<String, Object>) serverMap.get("server");
                Integer id = (Integer) server.get("id");
                serverDao.deleteServerById(id.longValue());
            }
        }
        return Result.success(200, "fail", null);
    }

    @Override
    public Result queryServer(String selectLabel, String queryInfo, Long currentPage, Long pageSize, Long project_id) {
        List<Object> data = new ArrayList<>();

        // get serverList & resultList
        List<Server> serverList = serverDao.getServer(project_id);
        List<Server> resultList = new ArrayList<>();
        for (Server server: serverList) {
            if (selectLabel.equals("IP")) {
                if (server.getIp().contains(queryInfo)) {
                    resultList.add(server);
                }
            } else if (selectLabel.equals("端口")) {
                if (server.getPort().longValue() == Long.valueOf(queryInfo)) {
                    resultList.add(server);
                }
            } else if (selectLabel.equals("用户名")) {
                if (server.getUsername().contains(queryInfo)) {
                    resultList.add(server);
                }
            } else if (selectLabel.equals("状态")) {
                if (queryInfo.equalsIgnoreCase("CONNECTED") && server.getStatus().equals(Server.Status.CONNECTED)) {
                    resultList.add(server);
                } else if (queryInfo.equalsIgnoreCase("DISCONNECTED") && server.getStatus().equals(Server.Status.DISCONNECTED)) {
                    resultList.add(server);
                }
            } else if (selectLabel.equals("创建时间")) {
                LocalDateTime dateTime = server.getCreated();
                String date = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                if (date.contains(queryInfo)) {
                    resultList.add(server);
                }
            } else if (selectLabel.equals("工具")) {
                String collectToolName = serverDao.getCollectToolByServerId(server.getId()).getName();
                if (collectToolName.contains(queryInfo)) {
                    resultList.add(server);
                }
            }
        }

        int resultAmount = resultList.size();
        int begin = pageSize.intValue() * (currentPage.intValue() - 1);
        int end = Math.min(pageSize.intValue() * currentPage.intValue(), resultAmount);

        resultList = resultList.subList(begin, end);
        resultAmount = resultList.size();

        for (Server server: resultList) {
            Map<String, Object> dataItem = new HashMap<>();
            dataItem.put("serverAmount", resultAmount);
            dataItem.put("server", server);
            dataItem.put("collectToolName", serverDao.getCollectToolByServerId(server.getId()).getName());
            data.add(dataItem);
        }

        return Result.success(200, "success", data);
    }

}
