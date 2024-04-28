package com.processmining.logdeploy.autodeploy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
public class Project implements Serializable {

    private Long id;

    @NotNull(message = "项目名称不能为空")
    private String name;

    private String description;

    @NotNull(message = "用户id不能为空")
    private Long user_id;

}
