package com.processmining.logdeploy.autodeploy.util;

import com.processmining.logdeploy.autodeploy.service.impl.ServerServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

public class PackageDetect {

    public static final String RESULT_DIRECTORY_PATH = "package";

    @Autowired
    private ServerServiceImpl serverServiceImpl;

    /**
     * @param file: application path
     * @param applicationName: 最后生成的包名文件路径为: PackageDetect.resultDirectory + applicationName
     */
    public Boolean resultFile(File file, String applicationName) {
        List<String> directories = new ArrayList<>();
        directories = serverServiceImpl.getFileDirectories(file, directories);
        List<String> result = filterUsingRE(directories);

        File dir = new File(RESULT_DIRECTORY_PATH);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        File resultFile = new File(RESULT_DIRECTORY_PATH + "/" + applicationName);
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

    public static List<String> filterUsingRE(List<String> absolutePaths) {
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


}
