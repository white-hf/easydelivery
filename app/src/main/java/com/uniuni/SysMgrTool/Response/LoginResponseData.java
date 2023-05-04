package com.uniuni.SysMgrTool.Response;

public class LoginResponseData {
    private int id;
    private String model;
    private String username;
    private int level;
    private int city_id;
    private String token;
    public void setId(int id) {
        this.id = id;
    }
    public int getId() {
        return id;
    }

    public void setModel(String model) {
        this.model = model;
    }
    public String getModel() {
        return model;
    }

    public void setUsername(String username) {
        this.username = username;
    }
    public String getUsername() {
        return username;
    }

    public void setLevel(int level) {
        this.level = level;
    }
    public int getLevel() {
        return level;
    }

    public void setCity_id(int city_id) {
        this.city_id = city_id;
    }
    public int getCity_id() {
        return city_id;
    }

    public void setToken(String token) {
        this.token = token;
    }
    public String getToken() {
        return token;
    }

}
