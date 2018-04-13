package com.dnnt.touch.domain;

public class User {

    public static final String USER_NAME="userName";
    public static final String PHONE = "phone";
    public static final String ID = "id";
    public static final String HEAD_URL = "headUrl";

    private long id;
    private String userName;
    private String password;
    private String phone;
    private String headUrl;

    public User(){}

    public User(long id, String userName, String phone, String password, String headUrl) {
        this.id = id;
        this.userName = userName;
        this.phone = phone;
        this.password = password;
        this.headUrl = headUrl;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getHeadUrl() {
        return headUrl;
    }

    public void setHeadUrl(String headUrl) {
        this.headUrl = headUrl;
    }
}
