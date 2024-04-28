package com.processmining.logdeploy.autodeploy.service.impl;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import com.processmining.logdeploy.autodeploy.common.lang.Result;
import com.processmining.logdeploy.autodeploy.controller.ProjectController;
import com.processmining.logdeploy.autodeploy.controller.ServerController;
import com.processmining.logdeploy.autodeploy.controller.UserController;
import com.processmining.logdeploy.autodeploy.dao.CollectDao;
import com.processmining.logdeploy.autodeploy.dao.ProjectDao;
import com.processmining.logdeploy.autodeploy.entity.*;
import com.processmining.logdeploy.autodeploy.service.CollectService;
import com.processmining.logdeploy.autodeploy.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CollectServiceImpl implements CollectService {

    @Autowired
    private CollectDao collectDao;     //关联dao
    @Autowired
    private ProjectController projectController;
    @Autowired
    private UserController userController;
    @Autowired
    private ServerController sController;

    public List<String> getFilePath(File file, List<String> paths) {    //非实现接口
        File[] files = file.listFiles();
        if (files == null) {
            return paths;
        }
        for (File _file: files) {
            if (_file.isFile()) {
                paths.add(_file.getAbsolutePath());
            }
            if (_file.isDirectory()) {
                getFilePath(_file, paths);
            }
        }
        return paths;
    }

    public String getUseCasePath(String applicationName, String useCaseName) {    //非实现接口
        String targetDirectory = null;

        File file = new File(Path.APPLICATION);
        List<String> directories = new ArrayList<>();
        directories = userController.getFileDirectories(file, directories);
        String slash = "(\\\\|\\\\\\\\|/|//)";
        String pattern = ".*" + applicationName + slash + "target";
        for (String directory: directories) {
            if (Pattern.matches(pattern, directory)) {
                targetDirectory = directory;
            }
        }

        List<String> paths = new ArrayList<>();
        paths = getFilePath(new File(targetDirectory), paths);
        for (String path: paths) {
            String pathPattern = ".*" + slash + useCaseName + ".class";
            if (Pattern.matches(pathPattern, path)) {
                int begin = -1;
                for (int i = 0; i < path.length(); i++) {
                    if (path.charAt(i) == '/' || path.charAt(i) == '\\') {
                        String target = path.substring(i + 1, i + 7);
                        if (target.equals("target") && (path.charAt(i + 7) == '/' || path.charAt(i + 7) == '\\')) {
                            begin = i + 1;
                        }
                    }
                }
                int end = path.length() - useCaseName.length() - ".class".length() - 1;

                StringBuilder result = new StringBuilder();
                for (int i = begin; i < end; i++) {
                    if (path.charAt(i) == '/' || path.charAt(i) == '\\') {
                        result.append('/');
                    } else {
                        result.append(path.charAt(i));
                    }
                }

                return result.toString();
            }
        }

        return null;
    }

    /**
     * return such as:
     * [
     *      {
     *          id: server_id or cluster_id
     *          value: server_ip or cluster_name
     *      }, ...
     * ]
     */
    @Override
    public Result getNodes(Long project_id, String type) {
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

    @Override
    public Result addDeploy(MultipartFile file, String type, Long nodeId, String directory, Long project_id, Boolean is_execute, String script, String code) {
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

        return Result.success(200, "应用部署成功", null);
    }

    /**
     * 缺点：deployAmount 冗余
     * return such as:
     * [
     *      {
     *          deployAmount: deployAmount,
     *          deploy: deploy,
     *          node: server_ip + server_port || cluster_name
     *      }, ...
     * ]
     */
    @Override
    public Result getDeploy(Long currentPage, Long pageSize, Long project_id) {   //情况2
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

    @Override
    public Result deleteDeploy(Deploy deploy) {
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

    @Override
    public Result deleteDeployBatch(List<Map<String, Object>> mapList) {
        if (mapList.size() == 0) {
            return Result.success(200, "批量撤销应用部署成功", null);
        }
        else {
            for (Map<String, Object> dataMap: mapList) {
                Map<String, Object> deployMap = (Map<String, Object>) dataMap.get("deploy");

                // construct deploy
                Deploy deploy = new Deploy();

                deploy.setId(((Integer) deployMap.get("id")).longValue());
                deploy.setApplication_name((String) deployMap.get("application_name"));
                deploy.setDirectory((String) deployMap.get("directory"));
                deploy.setCreated(LocalDateTime.parse((String) deployMap.get("created")));
                deploy.setCompression_format((String) deployMap.get("compression_format"));
                deploy.setProject_id(((Integer) deployMap.get("project_id")).longValue());

                String type = (String) deployMap.get("type");
                if (type.equals("SERVER")) {
                    deploy.setServer_id(((Integer) deployMap.get("server_id")).longValue());
                    deploy.setType(Deploy.Type.SERVER);
                } else if (type.equals("CLUSTER")) {
                    deploy.setCluster_id(((Integer) deployMap.get("cluster_id")).longValue());
                    deploy.setType(Deploy.Type.CLUSTER);
                }

                // call deleteDeploy
                deleteDeploy(deploy);
            }
        }

        return Result.success(200, "批量撤销应用部署成功", null);
    }

    @Override
    public Result queryDeploy(String selectLabel, String queryInfo, Long currentPage, Long pageSize, Long project_id) {
        List<Object> data = new ArrayList<>();

        // get resultList
        List<Deploy> resultList = new ArrayList<>();

        List<Deploy> deployList = collectDao.getDeploy(project_id);
        for (Deploy deploy: deployList) {
            switch (selectLabel) {
                case "应用名":
                    if (deploy.getApplication_name().contains(queryInfo))
                        resultList.add(deploy);
                    break;
                case "部署服务器":
                    if (deploy.getType().equals(Deploy.Type.SERVER)) {
                        Server server = collectDao.getServerById(deploy.getServer_id());
                        String node = server.getIp() + ":" + server.getPort();
                        if (node.contains(queryInfo))
                            resultList.add(deploy);
                    }
                    break;
                case "部署集群":
                    if (deploy.getType().equals(Deploy.Type.CLUSTER)) {
                        Cluster cluster = collectDao.getClusterById(deploy.getCluster_id());
                        if (cluster.getName().contains(queryInfo))
                            resultList.add(deploy);
                    }
                    break;
                case "部署目录":
                    if (deploy.getDirectory().contains(queryInfo))
                        resultList.add(deploy);
                    break;
                case "部署时间":
                    LocalDateTime dateTime = deploy.getCreated();
                    String date = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    if (date.contains(queryInfo))
                        resultList.add(deploy);
                    break;
            }
        }

        int resultAmount = resultList.size();
        int begin = pageSize.intValue() * (currentPage.intValue() - 1);
        int end = Math.min(pageSize.intValue() * currentPage.intValue(), resultAmount);
        resultList = resultList.subList(begin, end);

        for (Deploy deploy: resultList) {
            Map<String, Object> dataItem = new HashMap<>();

            dataItem.put("deploy", deploy);
            dataItem.put("deployAmount", resultAmount);
            if (deploy.getType().equals(Deploy.Type.SERVER)) {
                Server server = collectDao.getServerById(deploy.getServer_id());
                String node = server.getIp() + ":" + server.getPort();
                dataItem.put("node", node);
            } else if (deploy.getType().equals(Deploy.Type.CLUSTER)) {
                Cluster cluster = collectDao.getClusterById(deploy.getCluster_id());
                dataItem.put("node", cluster.getName());
            }

            data.add(dataItem);
        }

        return Result.success(200, "查询应用部署记录成功", data);
    }

    @Override
    public Result uploadServerScript(MultipartFile script, Long project_id, String applicationName) {
        String localScriptPath = Path.LOCAL_SCRIPT(collectDao.getUserName(project_id), collectDao.getProjectName(project_id), applicationName);

        // save script to local project
        userController.saveFile(script, localScriptPath);

        return Result.success(null);
    }

    @Override
    public Result getServerScript(Long project_id, String applicationName, String script) {
        String scriptFilePath = Path.LOCAL_SCRIPT(collectDao.getUserName(project_id), collectDao.getProjectName(project_id), applicationName) + "/" + script;

        String scriptContent = "";
        try (Stream<String> lines = Files.lines(Paths.get(scriptFilePath))) {
            scriptContent = scriptContent + lines.collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Result.success(scriptContent);
    }

    /**
     * return such as:
     *      {
     *          selectApplicationDetail: {
     *              application_name: application_name
     *              node: node
     *              directory: directory
     *              created: created
     *          }
     *          collectPackageList: [
     *              {
     *                  name: name
     *                  is_collect: is_collect
     *              }, ...
     *          ]
     *      }
     */
    @Override
    public Result getCollectFormDetail(Long id) {
        Map<String, Object> data = new HashMap<>();

        // selectApplicationDetail
        Map<String, Object> selectApplicationDetail = new HashMap<>();
        Deploy deploy = collectDao.getDeployById(id);

        selectApplicationDetail.put("application_name", deploy.getApplication_name());
        selectApplicationDetail.put("directory", deploy.getDirectory());
        selectApplicationDetail.put("created", deploy.getCreated().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        if (deploy.getType().equals(Deploy.Type.SERVER)) {
            Server server = collectDao.getServerById(deploy.getServer_id());
            String node = server.getIp() + ":" + server.getPort();
            selectApplicationDetail.put("node", node);
        } else if (deploy.getType().equals(Deploy.Type.CLUSTER)) {
            Cluster cluster = collectDao.getClusterById(deploy.getCluster_id());
            selectApplicationDetail.put("node", cluster.getName());
        }

        data.put("selectApplicationDetail", selectApplicationDetail);

        // collectPackageList
        List<Object> collectPackageList = new ArrayList<>();

        String detectFile = sController.RESULT_DIRECTORY_PATH + "/" + deploy.getApplication_name();
        try {
            FileReader fileReader = new FileReader(detectFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line = bufferedReader.readLine();

            while (line != null) {
                Map<String, Object> collectPackage = new HashMap<>();

                StringBuilder name = new StringBuilder();
                for (int i = 0; i < line.length(); i++) {
                    if (line.charAt(i) == '/' || line.charAt(i) == '\\')
                        name.append('.');
                    else
                        name.append(line.charAt(i));
                }

                collectPackage.put("name", name.toString());
                collectPackage.put("is_collect", true);
                collectPackageList.add(collectPackage);

                line = bufferedReader.readLine();
            }

            bufferedReader.close();
            fileReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        data.put("collectPackageList", collectPackageList);

        return Result.success(200, "获取采集表单信息成功", data);
    }

    @Override
    public Result addCollect(String name, Long deploy_id, Boolean is_default, String log_name,
                             String use_case_name, Long project_id, List<Map<String, Object>> collectPackageList) {
        // log_name and collectPackageList preprocessing
        if (is_default) {
            log_name = name;
            for (Map<String, Object> collectPackageItem: collectPackageList) {
                collectPackageItem.put("is_collect", true);
            }
        }

        // add collect to database
        Collect collect = new Collect();
        collect.setName(name);
        collect.setDeploy_id(deploy_id);
        collect.setCreated(LocalDateTime.now());
        collect.setIs_default(is_default);
        collect.setLog_name(log_name);
        collect.setUse_case_name(use_case_name);
        collect.setProject_id(project_id);
        collect.setIs_build(false);
        collectDao.addCollect(collect);

        // add collectPackage to database
        Long collect_id = collect.getId();
        for (Map<String, Object> collectPackageItem: collectPackageList) {
            CollectPackage collectPackage = new CollectPackage();

            collectPackage.setName((String) collectPackageItem.get("name"));
            collectPackage.setIs_collect((Boolean) collectPackageItem.get("is_collect"));
            collectPackage.setProject_id(project_id);
            collectPackage.setCollect_id(collect_id);

            collectDao.addCollectPackage(collectPackage);
        }

        // generate agent-config.xml & config.xml and save in local project
        projectController.GenerateAgentConfig(collectDao.getUserName(project_id), name, collectDao.getCollectPackage(project_id, collect_id), log_name);

        return Result.success(200, "新建采集任务成功", null);
    }

    /**
     * return such as:
     * [
     *      {
     *          collectAmount: collectAmount
     *          collect: collect
     *          application_name: application_name
     *          node: node
     *      }, ...
     * ]
     */
    @Override
    public Result getCollect(Long currentPage, Long pageSize, Long project_id) {
        List<Map<String, Object>> data = new ArrayList<>();

        // get collectAmount
        int collectAmount = collectDao.getCollect(project_id).size();

        // get collectList
        int begin = pageSize.intValue() * (currentPage.intValue() - 1);
        int end = Math.min(pageSize.intValue() * currentPage.intValue(), collectAmount);
        List<Collect> collectList = collectDao.getCollect(project_id).subList(begin, end);

        // get application_name, node and put (collectAmount, collect, application_name, node) to dataItem(Map)
        for (Collect collect: collectList) {
            Map<String, Object> dataItem = new HashMap<>();

            Deploy deploy = collectDao.getDeployById(collect.getDeploy_id());
            dataItem.put("collectAmount", collectAmount);
            dataItem.put("collect", collect);
            dataItem.put("application_name", deploy.getApplication_name());

            if (deploy.getType().equals(Deploy.Type.SERVER)) {
                Server server = collectDao.getServerById(deploy.getServer_id());
                String node = server.getIp() + ":" + server.getPort();
                dataItem.put("node", node);
            } else if (deploy.getType().equals(Deploy.Type.CLUSTER)) {
                Cluster cluster = collectDao.getClusterById(deploy.getCluster_id());
                dataItem.put("node", cluster.getName());
            }

            data.add(dataItem);
        }

        return Result.success(200, "获取采集任务成功", data);
    }

    @Override
    public Result buildCollect(Long id) {    //核心功能
        Collect collect = collectDao.getCollectById(id);
        Deploy deploy = collectDao.getDeployById(collect.getDeploy_id());
        Server server = collectDao.getServerById(deploy.getServer_id());

        Session session = null;

        Server.Status status = server.testConnectivity();
        if (status.equals(Server.Status.CONNECTED)) {
            try {
                session = server.sshSession();
                session.connect(Server.CONNECT_TIMEOUT);

                // upload start_server.sh and stop_server.sh
                JSchUtils.remoteExecute(session, "mkdir -p " + Path.REMOTE_SHELL(server.getUsername()));
                JSchUtils.upload(session, Path.START_SERVER, Path.REMOTE_SHELL(server.getUsername()));
                JSchUtils.upload(session, Path.STOP_SERVER, Path.REMOTE_SHELL(server.getUsername()));
                JSchUtils.remoteExecute(session, "cd " + Path.REMOTE_SHELL(server.getUsername()) +
                        "&& chmod 777 start_server.sh && chmod 777 stop_server.sh " +
                        "&& sed -i 's/\\r$//' start_server.sh && sed -i 's/\\r$//' stop_server.sh");

                // mkdir remoteConfigPath if don't exist
                String remoteConfigPath = Path.REMOTE_CONFIG(server.getUsername(), collect.getName());
                JSchUtils.remoteExecute(session, "mkdir -p " + remoteConfigPath);

                // upload agent-config.xml and config.xml to remote server
                File configFile = new File(Path.LOCAL_CONFIG(collectDao.getUserName(collect.getProject_id()), collect.getName()));
                if (configFile.exists() && configFile.isDirectory()) {
                    File[] files = configFile.listFiles();
                    if (files != null) {
                        for (File file: files) {
                            if (file.isFile()) {
                                JSchUtils.upload(session, file.getAbsolutePath(), remoteConfigPath);
                            }
                        }
                    }
                }

                // copy config.xml to CollectTool.DEFAULT_DIRECTORY
                String cp = "cp " + Path.REMOTE_CONFIG_XML(server.getUsername(), collect.getName()) + " " + Path.REMOTE_TOOL(server.getUsername());
                JSchUtils.remoteExecute(session, cp);

                // run start_server.sh
                String run_server = "cd " + Path.REMOTE_SHELL(server.getUsername()) + " && ./start_server.sh " + server.getUsername();
                JSchUtils.remoteExecute(session, run_server);

            } catch (JSchException | SftpException e) {
                e.printStackTrace();
            }
        }

        // 新建连接, 防止 log_server 监听进程关闭
        Session _session = null;

        try {
            _session = server.sshSession();
            _session.connect(Server.CONNECT_TIMEOUT);

            // cd useCasePath
            String cdUseCase = "cd " + deploy.getDirectory() + "/" + deploy.getApplication_name() + "/" +
                    getUseCasePath(deploy.getApplication_name(), collect.getUse_case_name());

            // run useCase
            String javaagent = "-javaagent:" + Path.REMOTE_TOOL(server.getUsername()) + "/log_agent-0.0.3-SNAPSHOT.jar";
            String agentConfigPath = Path.REMOTE_AGENT_CONFIG_XML(server.getUsername(), collect.getName());
            String runUseCase = "java " + javaagent + "=" + agentConfigPath + " " + collect.getUse_case_name();
            List<String> runUseCaseResult = JSchUtils.remoteExecute(_session, cdUseCase + " && " + runUseCase);
            System.out.println("runUseCaseResult: " + runUseCaseResult);

            // run stop_server.sh
            String stop_server = "cd " + Path.REMOTE_SHELL(server.getUsername()) + " && ./stop_server.sh";
            JSchUtils.remoteExecute(_session, stop_server);

            // get log and sava in local project
            File log = new File(Path.LOG);
            if (!log.exists())
                log.mkdirs();

            String source = Path.REMOTE_TOOL(server.getUsername()) + "/" + collect.getLog_name() + ".txt";
            String destination = Path.LOG;
            JSchUtils.download(_session, source, destination);

            // solve size of log.txt == 0 temporarily
            List<String> wcResult = JSchUtils.remoteExecute(_session,
                    "wc -c " + Path.REMOTE_TOOL(server.getUsername()) + "/" + collect.getLog_name() + ".txt");
            System.out.println(wcResult);
            System.out.println(wcResult.get(0).split(" ")[0].equals("0"));

            if (wcResult.get(0).split(" ")[0].equals("0"))
                return Result.success(200, "日志为空", null);

        } catch (JSchException | SftpException e) {
            e.printStackTrace();
        }

        if (_session != null && _session.isConnected())
            _session.disconnect();

        if (session != null && session.isConnected())
            session.disconnect();

        collectDao.updateIsBuild(collect.getId(), true);

        return Result.success(200, "日志采集成功", null);
    }

    @Override
    public void downloadLog(Long id) {
        String logName = collectDao.getCollectById(id).getLog_name() + ".txt";

        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletResponse response = requestAttributes.getResponse();
        String type = new MimetypesFileTypeMap().getContentType(logName);
        response.setHeader("Content-type", type);
        String encode = new String(logName.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        response.setHeader("Content-Disposition", "attachment; filename=" + encode);

        try {
            OutputStream outputStream = response.getOutputStream();
            byte[] buffer = new byte[1024];
            BufferedInputStream bufferedInputStream = new BufferedInputStream(new FileInputStream(Path.LOG + "/" + logName));
            int i = bufferedInputStream.read(buffer);
            while (i != -1) {
                outputStream.write(buffer, 0, buffer.length);
                outputStream.flush();
                i = bufferedInputStream.read(buffer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    public Result previewAgentConfig(Long collect_id) {
        Collect collect = collectDao.getCollectById(collect_id);
        String username = collectDao.getUserName(collect.getProject_id());
        String collectName = collect.getName();

        String agentConfigPath = Path.LOCAL_CONFIG(username, collectName) + "/agent-config.xml";

        String agentConfig = "";
        try (Stream<String> lines = Files.lines(Paths.get(agentConfigPath))) {
            agentConfig = agentConfig + lines.collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Result.success(agentConfig);
    }

    @Override
    public Result previewConfig(Long collect_id) {
        Collect collect = collectDao.getCollectById(collect_id);
        String username = collectDao.getUserName(collect.getProject_id());
        String collectName = collect.getName();

        String configPath = Path.LOCAL_CONFIG(username, collectName) + "/config.xml";

        String config = "";
        try (Stream<String> lines = Files.lines(Paths.get(configPath))) {
            config = config + lines.collect(Collectors.joining(System.lineSeparator()));
        } catch (IOException e) {
            e.printStackTrace();
        }

        return Result.success(config);
    }

    @Override
    public Result getServer(Long project_id) {
        return Result.success(collectDao.getServer(project_id));
    }

    @Override
    public Result addCluster(Cluster cluster) {
        cluster.setCreated(LocalDateTime.now());
        collectDao.addCluster(cluster);

        for (Server server: cluster.getServerList()) {
            collectDao.addClusterServer(cluster.getId(), server.getId());
        }

        return Result.success(null);
    }


}
