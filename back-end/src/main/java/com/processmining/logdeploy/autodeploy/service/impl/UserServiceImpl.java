package com.processmining.logdeploy.autodeploy.service.impl;

import com.processmining.logdeploy.autodeploy.dao.UserDao;
import com.processmining.logdeploy.autodeploy.entity.User;
import com.processmining.logdeploy.autodeploy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDao userDao;

    @Override
    public User getUserById(Long id) {
        return userDao.getUserById(id);
    }

    @Override
    public User getUserByName(String username) {
        return userDao.getUserByName(username);
    }

    @Override
    public int register(User user) {
        return userDao.register(user);
    }

}
