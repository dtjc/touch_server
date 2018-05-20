package com.dnnt.touch.util;

public class Constant {
    public static final boolean DEBUG = true;

    public static final String MSG_HANDLER = "msgHandler";

    public static final String TOKEN_KEY = "!r,,zGLkX7T^Rs60+tQYssiThTRFn@IZ(|NUy599aD[f>R`=DK.rM@X[VJOgiho";


    public static final int TYPE_HEARTBEAT = 0;
    public static final int TYPE_MSG = 1;
    public static final int TYPE_CONNECTED = 2;
    public static final int TYPE_ACK = 4;
    public static final int TYPE_ADD_FRIEND = 8;
    public static final int TYPE_FRIEND_AGREE = 0x10;
    public static final int TYPE_USER_NOT_EXIST=0x20;
    public static final int TYPE_USER_ALREADY_ADD=0x40;
    public static final int TYPE_HEAD_UPDATE = 0x80;
    public static final int TYPE_SEND_FAIL = 0x100;
    public static final int TYPE_TOKEN_WRONG = 0x200;
    public static final int TYPE_OTHER_LOGIN = 0x800;
    public static final char SPLIT_CHAR = ';';

    public static final int CODE_TAG_REGISTER = 1;
    public static final int CODE_TAG_RESET = 2;

    public static final String REAL_HEAD_DIR = "/image/head/";
    public static final String MAPPING_HEAD_DIR = "/user/head/";
}
