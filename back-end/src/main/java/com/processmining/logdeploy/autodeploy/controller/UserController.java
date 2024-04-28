package com.processmining.logdeploy.autodeploy.controller;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.map.MapUtil;
import cn.hutool.crypto.SecureUtil;
import com.processmining.logdeploy.autodeploy.entity.Path;
import com.processmining.logdeploy.autodeploy.entity.User;
import com.processmining.logdeploy.autodeploy.common.dto.LoginDto;
import com.processmining.logdeploy.autodeploy.common.lang.Result;
import com.processmining.logdeploy.autodeploy.service.UserService;
import com.processmining.logdeploy.autodeploy.util.JwtUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

@RestController
public class UserController {

    @Autowired
    UserService userService;

    @Autowired
    JwtUtils jwtUtils;

    @PostMapping("/login")
    public Result login(@Validated @RequestBody LoginDto loginDto, HttpServletResponse response) {   //前端通过LoginDto将请求体里的数据传入login方法
        User user = userService.getUserByName(loginDto.getUsername());

        Assert.notNull(user, "用户不存在");
        if (!user.getPassword().equals(SecureUtil.md5(loginDto.getPassword()))) {
            return Result.fail("密码不正确");
        }

        String jwt = jwtUtils.generateToken(user.getId());

        response.setHeader("Authorization", jwt);
        response.setHeader("Access-control-Expose-Headers", "Authorization");

        return Result.success(200, "Login Succeed!", MapUtil.builder()
                .put("id", user.getId())
                .put("username", user.getUsername())
                .put("avatar", user.getAvatar())
                .put("email", user.getEmail())
                .map()
        );
    }

    @RequiresAuthentication
    @GetMapping("/logout")
    public Result logout() {
        SecurityUtils.getSubject().logout();
        return Result.success(200, "Logout Succeed!", null);
    }

    @PostMapping("/register")
    public Result register(@Validated @RequestBody User user) {
        User temp_user = userService.getUserByName(user.getUsername());

        if (temp_user != null) {
            return Result.fail("该用户名已被使用");
        }

        user.setPassword(SecureUtil.md5(user.getPassword()));
        user.setStatus(User.Status.ACTIVE);
        userService.register(user);
        return Result.success(200, "Register Succeed!", null);
    }

    public static boolean saveFile(MultipartFile file, String directory) {
        File _file = new File(directory);
        if (!_file.exists()) {
            _file.mkdirs();
        }
        try {
            BufferedOutputStream out = new BufferedOutputStream(
                    new FileOutputStream(directory + "/" + file.getOriginalFilename()));
            out.write(file.getBytes());
            out.flush();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

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

    public List<String> filterUsingRE(List<String> absolutePaths) {
        List<String> packages = new ArrayList<>();
        String slash = "(\\\\|\\\\\\\\|/|//)";
        String pattern = ".*src" + slash + "main" + slash + "java" + slash + ".*" + slash + ".*" + slash + ".*" + slash + ".*";
        for (String path: absolutePaths) {
            if (Pattern.matches(pattern, path)) {
                String[] substring = new String[5];
                substring[0] = "src/main/java";
                substring[1] = "src//main//java";
                substring[2] = "src\\main\\java";
                substring[3] = "src\\\\main\\\\java";
                substring[4] = "src\\\\\\\\main\\\\\\\\java";
                int index = -1;
                for (String s: substring) {
                    index = Math.max(index, path.indexOf(s));
                }
                String relativePath = path.substring(index + 14);
                packages.add(relativePath);
            }
        }

        List<String> result = new ArrayList<>();
        for (String _package: packages) {
            if (!result.contains(_package)) {
                result.add(_package);
            }
        }

        Collections.sort(result, new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                return o1.compareTo(o2);
            }
        });

        return result;
    }

    /**
     * @param file: application path
     * @param applicationName: 最后生成的包名文件路径为: PackageDetect.resultDirectory + applicationName
     */
    public Boolean resultFile(File file, String applicationName) {
        List<String> directories = new ArrayList<>();
        directories = getFileDirectories(file, directories);
        List<String> result = filterUsingRE(directories);

        File dir = new File("package");
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File resultFile = new File("package" + "/" + applicationName);
        if (resultFile.exists() && resultFile.isFile()) {
            resultFile.delete();
        }

        try {
            resultFile.createNewFile();
            FileOutputStream fileOutputStream = new FileOutputStream(resultFile);
            BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
            for (String _package: result) {
                bufferedOutputStream.write((_package + "\n").getBytes());
            }
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
