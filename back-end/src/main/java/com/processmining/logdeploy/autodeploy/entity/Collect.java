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
public class Collect implements Serializable {

    private Long id;

    @NotNull(message = "采集任务名称不能为空")
    private String name;

    @NotNull(message = "部署id不能为空")
    private Long deploy_id;

    private LocalDateTime created;
    private Boolean is_default;
    private String log_name;
    private String use_case_name;

    @NotNull(message = "项目id不能为空")
    private Long project_id;

    private Boolean is_build;

}
