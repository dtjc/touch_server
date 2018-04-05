package com.dnnt.touch.domain;

public class Json<T> {
//    var msg: String = "", var successful: Boolean = false, var obj: T? = null, var code: Int = -1
    private String msg;
    private boolean successful;
    private T obj;
    private int code;

    public Json(){}

    public Json(String msg, boolean successful, T obj, int code) {
        this.msg = msg;
        this.successful = successful;
        this.obj = obj;
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }

    public T getObj() {
        return obj;
    }

    public void setObj(T obj) {
        this.obj = obj;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }
}
