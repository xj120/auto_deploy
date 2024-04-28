package com.processmining.logdeploy.autodeploy.entity;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Server implements Serializable {

    public static final int CONNECT_TIMEOUT = 5000;
    private static final String identity = "~/.ssh/id_rsa";
    private static final String passphrase = "";

    private Long id;

    @NotNull(message = "ip不能为空")
    private String ip;

    @NotNull(message = "端口不能为空")
    private Long port;

    @NotNull(message = "用户名不能为空")
    private String username;

    @NotNull(message = "密码不能为空")
    private String password;

    public enum Status {
        CONNECTED, DISCONNECTED
    }

    private Status status;

    @NotNull(message = "是否使用默认配置字段不能为空")
    private Boolean is_default;

    @NotNull(message = "项目id不能为空")
    private Long project_id;

    private LocalDateTime created;

    public Session sshSession() throws JSchException {  //错误的实现 Dao层
        JSch jSch = new JSch();
        if (Files.exists(Paths.get(identity))) {
            jSch.addIdentity(identity, passphrase);
        }
        Session session = jSch.getSession(username, ip, port.intValue());
        session.setPassword(password);
        session.setConfig("StrictHostKeyChecking", "no");
        return session;
    }

    public Status testConnectivity() {
        try {
            status = Status.DISCONNECTED;
            Session session = sshSession();
            session.connect(CONNECT_TIMEOUT);
            if (session.isConnected()) {
                status = Status.CONNECTED;
                session.disconnect();
            }
        } catch (JSchException e) {

        }
        return status;
    }

}
