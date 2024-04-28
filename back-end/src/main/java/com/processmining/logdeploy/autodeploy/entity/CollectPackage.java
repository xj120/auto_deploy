package com.processmining.logdeploy.autodeploy.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class CollectPackage implements Serializable {

    public static final String DEFAULT_DIRECTORY_PATH = "/home/zjt/autodeploy/packageDetect";

    private Long id;
    private String name;
    private Boolean is_collect;
    private Long project_id;
    private Long collect_id;

}
