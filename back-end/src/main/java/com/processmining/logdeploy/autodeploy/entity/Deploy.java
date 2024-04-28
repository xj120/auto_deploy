package com.processmining.logdeploy.autodeploy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Deploy implements Serializable {

    private Long id;

    @NotNull(message = "应用名不能为空")
    private String application_name;

    private Long server_id;
    private Long cluster_id;

    public enum Type {
        SERVER, CLUSTER
    }

    @NotNull(message = "应用部署类型不能为空")
    private Type type;

    @NotNull(message = "应用部署目录不能为空")
    private String directory;

    private LocalDateTime created;

    @NotNull(message = "应用压缩格式不能为空")
    private String compression_format;

    private Boolean is_execute;
    private String script_path;

    @NotNull(message = "项目id不能为空")
    private Long project_id;

}
