package com.processmining.logdeploy.autodeploy.controller;

import cn.hutool.json.JSONObject;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.processmining.logdeploy.autodeploy.common.lang.Result;
import com.processmining.logdeploy.autodeploy.dao.CollectDao;
import com.processmining.logdeploy.autodeploy.entity.*;
import com.processmining.logdeploy.autodeploy.service.CollectService;
import com.processmining.logdeploy.autodeploy.util.JSchUtils;
import com.processmining.logdeploy.autodeploy.util.UnZipUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/collect")
public class CollectController {

    @Autowired
    private CollectDao collectDao;     //关联dao
    @Autowired
    private CollectService collectService;   //关联Service
    @Autowired
    private UserController userController;
    @Autowired
    private ServerController sController;


    @GetMapping("/getNodes")
    @RequiresAuthentication
    public Result getNodes(@RequestParam("project_id") Long project_id, @RequestParam("type") String type) {
        List<Object> data = new ArrayList<>();

        if (type.equals("服务器")) {
            List<Server> serverList = collectDao.getServerByProjectId(project_id);
            for (Server server: serverList) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("id", server.getId());
                dataMap.put("value", server.getIp());
                data.add(dataMap);
            }
        }
        else if (type.equals("集群")) {
            List<Cluster> clusterList = collectDao.getClusterByProjectId(project_id);
            for (Cluster cluster: clusterList) {
                Map<String, Object> dataMap = new HashMap<>();
                dataMap.put("id", cluster.getId());
                dataMap.put("value", cluster.getName());
                data.add(dataMap);
            }
        }

        return Result.success(data);
    }
/*    @GetMapping("/test")
    @RequiresAuthentication
    public Result test(@RequestParam("project_id") Long project_id, @RequestParam("type") String type) {
        CollectController c=new CollectController();
        c.addCluster();
        return collectService.getNodes(project_id, type);
    }*/

    @PostMapping("/addDeploy")
    public Result addDeploy(@RequestParam("file") MultipartFile file, @RequestParam("type") String type,
                            @RequestParam("nodeId") Long nodeId, @RequestParam("directory") String directory,
                            @RequestParam("project_id") Long project_id, @RequestParam("is_execute") Boolean is_execute,
                            @RequestParam("script") String script, @RequestParam("code") String code) {
        long start = System.currentTimeMillis();

        String fileName = file.getOriginalFilename();

        Deploy deploy = new Deploy();
        deploy.setCreated(LocalDateTime.now());
        deploy.setProject_id(project_id);
        deploy.setIs_execute(is_execute);
        if (fileName.endsWith(Format.ZIP))
            deploy.setCompression_format(Format.ZIP);
        else if (fileName.endsWith(Format.TAR_GZ))
            deploy.setCompression_format(Format.TAR_GZ);
        else
            deploy.setCompression_format(Format.COMMON);

        // save application to local project
        String localApplicationPath = Path.LOCAL_APPLICATION(collectDao.getUserName(project_id));
        userController.saveFile(file, localApplicationPath);

        // update script to local project if is_execute == true
        String scriptLocalPath;
        File scriptLocalFile = null;
        if (is_execute) {
            scriptLocalPath = Path.LOCAL_SCRIPT(collectDao.getUserName(project_id), collectDao.getProjectName(project_id), fileName) + "/" + script;
            scriptLocalFile = new File(scriptLocalPath);

            try {
                FileOutputStream fileOutputStream = new FileOutputStream(scriptLocalFile);
                BufferedOutputStream outputStream = new BufferedOutputStream(fileOutputStream);

                String[] lines = code.split(System.lineSeparator());
                for (int i = 0; i < lines.length; i++) {
                    if (i == 0) {
                        outputStream.write(lines[i].getBytes(StandardCharsets.UTF_8));
                        continue;
                    }
                    outputStream.write((System.lineSeparator() + lines[i]).getBytes(StandardCharsets.UTF_8));
                }

                outputStream.flush();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (type.equals("服务器")) {
            // unzip application to local project
            if (fileName.endsWith(Format.ZIP)) {
                try {
                    UnZipUtils.unZipFiles(new File(localApplicationPath + "/" + fileName), localApplicationPath + "/");  //应该是Service
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // package detect and save result to local project
            int suffixLength = fileName.endsWith(Format.ZIP) ? Format.ZIP.length() : 0;
            String resultFileName = fileName.substring(0, fileName.length() - suffixLength);
            userController.resultFile(new File((localApplicationPath + "/" + fileName).substring(0,
                    (localApplicationPath + "/" + fileName).length() - suffixLength)), resultFileName);

            // upload file to remote server and decompression if file is compressed
            Server server = collectDao.getServerById(nodeId);
            Server.Status status = server.testConnectivity();
            if (status.equals(Server.Status.CONNECTED)) {
                try {
                    Session session = server.sshSession();
                    session.connect(Server.CONNECT_TIMEOUT);
                    JSchUtils.remoteExecute(session, "mkdir -p " + directory);

                    File dirFile = new File(localApplicationPath);
                    if (dirFile.exists() && dirFile.isDirectory()) {
                        File[] files = dirFile.listFiles();
                        if (files != null) {
                            for (File _file: files) {
                                if (_file.isFile()) {
                                    try {
                                        JSchUtils.upload(session, _file.getAbsolutePath(), directory);
                                    } catch (SftpException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                    }

                    if (fileName.endsWith(Format.ZIP)) {
                        if (directory.endsWith("/"))
                            directory = directory.substring(0, directory.length() - 1);
                        String unzipCmd = "unzip -od " + directory + "/ " + directory + "/" + fileName;
                        JSchUtils.remoteExecute(session, unzipCmd);
                    }

                    // upload script to remote server and execute script if is_execute == true
                    if (is_execute) {
                        String scriptRemotePath = Path.REMOTE_SCRIPT(server.getUsername(), fileName);
                        JSchUtils.remoteExecute(session, "mkdir -p " + scriptRemotePath);
                        JSchUtils.upload(session, scriptLocalFile.getAbsolutePath(), scriptRemotePath);
                        List<String> res = JSchUtils.remoteExecute(session, "cd " + scriptRemotePath +
                                " && chmod 777 " + script +
                                " && sed -i 's/\\r$//' " + script +
                                " && ./" + script);
                        System.out.println(res);
                    }

                    if (session.isConnected())
                        session.disconnect();

                } catch (JSchException | SftpException e) {
                    e.printStackTrace();
                }
            }

            // add deploy to database
            deploy.setApplication_name(resultFileName);
            deploy.setServer_id(nodeId);
            deploy.setType(Deploy.Type.SERVER);
            deploy.setDirectory(directory);
            if (is_execute)
                deploy.setScript_path(Path.REMOTE_SCRIPT(server.getUsername(), fileName));
            collectDao.addServerDeploy(deploy);
        }
        else if (type.equals("集群")) {
            // upload file to cluster
            Cluster cluster = collectDao.getCluster(nodeId);
            cluster.setServerList(collectDao.getServerList(cluster.getId()));

            for (Server server: cluster.getServerList()) {
                Server.Status status = server.testConnectivity();
                String remoteApplicationPath = Path.REMOTE_APPLICATION(server.getUsername());
                if (status.equals(Server.Status.CONNECTED)) {
                    try {
                        Session session = server.sshSession();
                        session.connect(Server.CONNECT_TIMEOUT);
                        JSchUtils.remoteExecute(session, "mkdir -p " + remoteApplicationPath);

                        File dirFile = new File(localApplicationPath);
                        if (dirFile.exists() && dirFile.isDirectory()) {
                            File[] files = dirFile.listFiles();
                            if (files != null) {
                                for (File _file: files) {
                                    if (_file.isFile()) {
                                        JSchUtils.upload(session, _file.getAbsolutePath(), remoteApplicationPath);
                                    }
                                }
                            }
                        }

                        // decompression if file is compressed
                        if (file.getOriginalFilename().endsWith(Format.TAR_GZ)) {
                            JSchUtils.remoteExecute(session, "cd " + remoteApplicationPath + " && " + "tar -zxf " + file.getOriginalFilename());
                        }

                        if (session.isConnected())
                            session.disconnect();

                    } catch (JSchException | SftpException e) {
                        e.printStackTrace();
                    }

                }
            }

            // add deploy to database
            String applicationName = fileName;
            if (applicationName.contains("."))
                applicationName = applicationName.substring(0, applicationName.indexOf('.'));
            deploy.setApplication_name(applicationName);
            deploy.setCluster_id(nodeId);
            deploy.setType(Deploy.Type.CLUSTER);
            deploy.setDirectory(Path.REMOTE_APPLICATION("server.username"));
            if (is_execute)
                deploy.setScript_path(Path.REMOTE_SCRIPT("server.username", applicationName));
            collectDao.addServerDeploy(deploy);
        }
        Result result = Result.success(200, "应用部署成功", null);
        long end = System.currentTimeMillis();
        System.out.println("Total time: " + (end - start));
        return result;
    }

    @PostMapping("/addCollect")
    @RequiresAuthentication
    public Result addCollect(@RequestBody JSONObject jsonParam) {   //JSONObject为Jason处理对象
        String name = jsonParam.get("name").toString();
        Long deploy_id = jsonParam.getLong("deploy_id");
        Boolean is_default = jsonParam.getBool("is_default");
        String log_name = jsonParam.get("log_name").toString();
        String use_case_name = jsonParam.get("use_case_name").toString();
        Long project_id = jsonParam.getLong("project_id");
        List<Map<String, Object>> collectPackageList = (List<Map<String, Object>>) jsonParam.get("collectPackageList");

        return collectService.addCollect(name, deploy_id, is_default, log_name, use_case_name, project_id, collectPackageList);
    }


    @GetMapping("/getDeploy")
    @RequiresAuthentication
    public Result getDeploy(@RequestParam("currentPage") Long currentPage, @RequestParam("pageSize") Long pageSize,
                            @RequestParam("project_id") Long project_id) {
        List<Object> data = new ArrayList<>();

        // get deployAmount
        int deployAmount = collectDao.getDeploy(project_id).size();

        // get deployList
        int begin = pageSize.intValue() * (currentPage.intValue() - 1);
        int end = Math.min(pageSize.intValue() * currentPage.intValue(), deployAmount);
        List<Deploy> deployList = collectDao.getDeploy(project_id).subList(begin, end);

        // get node and put (deployAmount, deploy, node) to dataItem(Map)
        for (Deploy deploy: deployList) {
            Map<String, Object> dataItem = new HashMap<>();
            dataItem.put("deployAmount", deployAmount);
            dataItem.put("deploy", deploy);
            if (deploy.getType().equals(Deploy.Type.SERVER)) {
                Server server = collectDao.getServerById(deploy.getServer_id());
                String node = server.getIp() + ":" + server.getPort();
                dataItem.put("node", node);
            } else if (deploy.getType().equals(Deploy.Type.CLUSTER)) {
                Cluster cluster = collectDao.getClusterById(deploy.getCluster_id());
                String node = cluster.getName();
                dataItem.put("node", node);
            }
            data.add(dataItem);
        }

        return Result.success(200, "获取应用部署列表成功", data);
    }

    @PostMapping("/deleteDeploy")
    @RequiresAuthentication
    public Result deleteDeploy(@RequestBody Deploy deploy) {
        if (deploy.getType().equals(Deploy.Type.SERVER)) {
            Server server = collectDao.getServerById(deploy.getServer_id());
            Server.Status status = server.testConnectivity();

            if (status.equals(Server.Status.CONNECTED)) {
                try {
                    Session session = server.sshSession();
                    session.connect(Server.CONNECT_TIMEOUT);

                    // delete application(decompressed) in remote server
                    String decompressedPath = deploy.getDirectory() + "/" + deploy.getApplication_name();
                    String rmDecompressedPackage = "rm -rf " + decompressedPath;
                    JSchUtils.remoteExecute(session, rmDecompressedPackage);

                    // delete application(compressed) in remote server
                    if (!deploy.getCompression_format().equals(Format.COMMON)) {
                        String compressedPath = deploy.getDirectory() + "/" + deploy.getApplication_name() + deploy.getCompression_format();
                        String rmCompressedPackage = "rm -rf " + compressedPath;
                        JSchUtils.remoteExecute(session, rmCompressedPackage);
                    }

                    if (session.isConnected())
                        session.disconnect();

                } catch (JSchException e) {
                    e.printStackTrace();
                }
            }
        }

        // delete deploy in database
        collectDao.deleteDeployById(deploy.getId());

        return Result.success(200, "撤销应用部署成功", null);
    }




}
