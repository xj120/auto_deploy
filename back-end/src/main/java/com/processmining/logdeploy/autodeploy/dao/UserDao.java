package com.processmining.logdeploy.autodeploy.dao;

import com.processmining.logdeploy.autodeploy.entity.User;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDao {

    User getUserById(@Param("id") Long id);
    User getUserByName(@Param("username") String username);
    int register(@Param("user") User user);

}
