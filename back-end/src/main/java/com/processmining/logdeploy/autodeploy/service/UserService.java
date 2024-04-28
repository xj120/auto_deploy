package com.processmining.logdeploy.autodeploy.service;

import com.processmining.logdeploy.autodeploy.entity.User;

public interface UserService {

    User getUserById(Long id);
    User getUserByName(String username);
    int register(User user);

}
