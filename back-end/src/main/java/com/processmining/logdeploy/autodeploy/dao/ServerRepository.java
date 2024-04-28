package com.processmining.logdeploy.autodeploy.dao;

import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ServerRepository {
    public boolean addSeverTool(File file, String applicationName) {
        List<String> directories = new ArrayList<>();
        directories = serverService.getFileDirectories(file, directories);
        if(directories==null)  return false;

        return true;
    }
}
