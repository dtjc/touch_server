package com.dnnt.touch.util;

public class Constant {
    public static final boolean DEBUG = true;

    public static final String MSG_HANDLER = "msgHandler";

    public static final int TYPE_HEARTBEAT = 0;
    public static final int TYPE_MSG = 1;
    public static final int TYPE_CONNECTED = 2;
    public static final int TYPE_ACK = 4;
    public static final int TYPE_ADD_FRIEND = 8;
    public static final int TYPE_FRIEND_AGREE = 0x10;
    public static final int TYPE_USER_NOT_EXIST=0x20;
    public static final int TYPE_USER_ALREADY_ADD=0x40;
    public static final char SPLIT_CHAR = ';';
}
