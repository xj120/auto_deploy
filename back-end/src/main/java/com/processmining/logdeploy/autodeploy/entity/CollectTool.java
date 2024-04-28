package com.processmining.logdeploy.autodeploy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CollectTool implements Serializable {

    private Long id;

    @NotNull(message = "采集工具名称不能为空")
    private String name;

    @NotNull(message = "采集工具部署目录不能为空")
    private String directory;

    @NotNull(message = "服务器id不能为空")
    private Long server_id;

}
