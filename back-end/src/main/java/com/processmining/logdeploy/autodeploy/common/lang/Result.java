package com.processmining.logdeploy.autodeploy.common.lang;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result implements Serializable {

    private int code;
    private String msg;
    private Object data;

    public static Result success(int code, String msg, Object data)
    {
        Result r = new Result();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    public static Result success(Object data)
    {
        return success(200, "succeed!", data);
    }

    public static Result fail(int code, String msg, Object data)
    {
        Result r = new Result();
        r.setCode(code);     //set方法由lombok工具提供实现
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    public static Result fail(String msg, Object data)
    {
        return fail(400, msg, data);
    }

    public static Result fail(String msg)
    {
        return fail(400, msg, null);
    }

}
