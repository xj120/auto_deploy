package com.processmining.logdeploy.autodeploy.controller;

import com.processmining.logdeploy.autodeploy.dao.ProjectDao;
import com.processmining.logdeploy.autodeploy.entity.Collect;
import com.processmining.logdeploy.autodeploy.entity.Path;
import com.processmining.logdeploy.autodeploy.entity.Project;
import com.processmining.logdeploy.autodeploy.common.lang.Result;
import com.processmining.logdeploy.autodeploy.service.ProjectService;
import com.processmining.logdeploy.autodeploy.service.impl.ProjectServiceImpl;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;


@CrossOrigin
@RequestMapping("/project")
@Controller
public class ProjectController {

    @Autowired
    private ProjectServiceImpl projectService;
    @Autowired
    private ProjectDao projectDao;

    @RequiresAuthentication
    @GetMapping("/getAllProject")
    public Result getAllProject(@RequestParam("user_id") Long user_id) {
        List<Object> data = new ArrayList<>();
        extracted(user_id, data);
        return Result.success(200, "获取项目列表成功", data);
    }

    private void extracted(Long user_id, List<Object> data) {
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

    @RequiresAuthentication
    @PostMapping("/addProject")
    public Result addProject(@RequestBody Project project) {
        int code = projectDao.addProject(project);
        String msg = code > 0 ? "项目创建成功" : "项目创建失败";
        code = code > 0 ? 200 : 400;
        return Result.success(code, msg, null);
    }



    @RequiresAuthentication
    @GetMapping("/deleteProject")
    public Result deleteProject(@RequestParam("id") Long id) {
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

    @RequiresAuthentication
    @GetMapping("/deleteCollect")
    public Result deleteCollect(@RequestParam("id") Long id) {
        return getResult(projectDao.deleteCollectByID(id), "删除成功", "删除失败");
    }

    private Result getResult(int i, String 删除成功, String 删除失败) {
        int code = i;
        String msg = code > 0 ? 删除成功 : 删除失败;
        code = code > 0 ? 200 : 400;
        return Result.success(code, msg, null);
    }

    @RequiresAuthentication
    @GetMapping("/searchProjectAndCollect")
    public Result searchProjectAndCollect(@RequestParam("user_id") Long user_id, @RequestParam("queryText") String queryText) {
        Map<String, Object> response = new HashMap<>();
        // data & state                                                             //典型案例
        List<Object> data = new ArrayList<>();                  //这种情况怎么重构，当return遍历个数为1时合并。
        Map<Long, String> state = new HashMap<>();

        List<Project> projects = projectDao.getAllProject(user_id);   //数据流一定要到终点结束
        for (int i = 0; i < projects.size(); i++) {
            Project project = projects.get(i);
            List<Collect> collects = projectDao.getCollect(project.getId());     //multipule valuable
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
        Project project1=projects.get(0);
        long id=project1.getId();
//        System.out.println(project1);
//        System.out.println(id);
        response.put("state", state);
        response.put("data", data);


        return Result.success(200, "查询成功", response);
    }

    public static Boolean GenerateAgentConfig(String username, String collectName, List<String> collectPackageList, String logName) {
        String applicationPattern = ".*<application></application>.*";
        String tierPattern = ".*<tier></tier>.*";
        String methodPointCutPattern = ".*<method-pointcut>.*";
        String threadCallPointCutPattern = ".*<thread-call-pointcut>.*";
        String filePattern = ".*<file></file>.*";

        File directory = new File(Path.LOCAL_CONFIG(username, collectName));
        if (!directory.exists())
            directory.mkdirs();

        File agentConfig = new File(Path.LOCAL_CONFIG(username, collectName) + "/agent-config.xml");
        if (agentConfig.exists() && agentConfig.isFile())
            agentConfig.delete();

        File config = new File(Path.LOCAL_CONFIG(username, collectName) + "/config.xml");
        if (config.exists() && config.isFile())
            config.delete();

        try {
            // generate agent-config.xml
            agentConfig.createNewFile();
            FileOutputStream acFileOutputStream = new FileOutputStream(agentConfig);
            BufferedOutputStream acBufferedOutputStream = new BufferedOutputStream(acFileOutputStream);

            File agentConfigTemplate = new File(Path.CONFIG + "/template/agent-config.xml");
            BufferedReader acBufferedReader = new BufferedReader(new FileReader(agentConfigTemplate));
            String readString = null;
            while ((readString = acBufferedReader.readLine()) != null) {
                if (Pattern.matches(applicationPattern, readString)) {
                    acBufferedOutputStream.write(("\t\t<application>" + collectName + "</application>\n").getBytes());
                } else if (Pattern.matches(tierPattern, readString)) {
                    acBufferedOutputStream.write(("\t\t<tier>" + collectName + "</tier>\n").getBytes());
                } else if (Pattern.matches(methodPointCutPattern, readString)) {
                    acBufferedOutputStream.write(("\t\t<method-pointcut>\n").getBytes());
                    acBufferedOutputStream.write(("\t\t\t<enabled>true</enabled>\n").getBytes());
                    for (String collectPackage: collectPackageList) {
                        acBufferedOutputStream.write(("\t\t\t<include>" + collectPackage + ".*</include>\n").getBytes());
                    }
                } else if (Pattern.matches(threadCallPointCutPattern, readString)) {
                    acBufferedOutputStream.write(("\t\t<thread-call-pointcut>\n").getBytes());
                    acBufferedOutputStream.write(("\t\t\t<enabled>true</enabled>\n").getBytes());
                    for (String collectPackage: collectPackageList) {
                        acBufferedOutputStream.write(("\t\t\t<include>" + collectPackage + ".*</include>\n").getBytes());
                    }
                } else {
                    acBufferedOutputStream.write((readString + "\n").getBytes());
                }
            }
            acBufferedReader.close();

            acBufferedOutputStream.flush();
            acBufferedOutputStream.close();

            // generate config.xml
            config.createNewFile();
            FileOutputStream cFileOutputStream = new FileOutputStream(config);
            BufferedOutputStream cBufferedOutputStream = new BufferedOutputStream(cFileOutputStream);

            File configTemplate = new File(Path.CONFIG + "/template/config.xml");
            BufferedReader cBufferedReader = new BufferedReader(new FileReader(configTemplate));
            String cReadString = null;
            while ((cReadString = cBufferedReader.readLine()) != null) {
                if (Pattern.matches(filePattern, cReadString)) {
                    cBufferedOutputStream.write(("\t\t\t<file>" + logName + ".txt</file>\n").getBytes());
                } else {
                    cBufferedOutputStream.write((cReadString + "\n").getBytes());
                }
            }
            cBufferedReader.close();

            cBufferedOutputStream.flush();
            cBufferedOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

}
