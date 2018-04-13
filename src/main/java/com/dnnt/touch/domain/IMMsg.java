package com.dnnt.touch.domain;

public class IMMsg {
    private long fromId;
    private long toId;
    private String msg;
    private long time;
    private int type;

    public IMMsg(long fromId, long toId, String msg, long time, int type) {
        this.fromId = fromId;
        this.toId = toId;
        this.msg = msg;
        this.time = time;
        this.type = type;
    }

    public long getFromId() {
        return fromId;
    }

    public void setFromId(long fromId) {
        this.fromId = fromId;
    }

    public long getToId() {
        return toId;
    }

    public void setToId(long toId) {
        this.toId = toId;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }
}
