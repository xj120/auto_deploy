package com.processmining.logdeploy.autodeploy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Cluster implements Serializable {

    private Long id;

    @NotNull(message = "集群名称不能为空")
    private String name;

    private String description;
    private LocalDateTime created;

    @NotNull(message = "项目id不能为空")
    private Long project_id;

    private List<Server> serverList;

}
