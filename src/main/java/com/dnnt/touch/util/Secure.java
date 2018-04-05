package com.dnnt.touch.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Secure {
    public static String bytesToHex(byte[] bytes){
        char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length << 1];
        for (int i = 0; i < bytes.length; i++){
            int b = bytes[i] & 0xFF;
            hexChars[i << 1] = hexArray[b >>> 4];
            hexChars[(i << 1) + 1] = hexArray[b & 0x0F];
        }
        return new String(hexChars);
    }

    public static String getMD5Hex(byte[] bytes){
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
            byte[] md5Bytes = digest.digest(bytes);
            return bytesToHex(md5Bytes);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }
}
