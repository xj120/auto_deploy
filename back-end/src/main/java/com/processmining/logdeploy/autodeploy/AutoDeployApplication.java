package com.processmining.logdeploy.autodeploy;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@MapperScan("com.processmining.logdeploy.autodeploy.dao")
@SpringBootApplication
public class AutoDeployApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext applicationContext = SpringApplication.run(AutoDeployApplication.class, args);
    }

}
