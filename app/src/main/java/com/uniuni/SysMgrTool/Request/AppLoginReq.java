package com.uniuni.SysMgrTool.Request;

public class AppLoginReq {
    private String credential_id;
    private String password;
    public void setCredential_id(String credential_id) {
        this.credential_id = credential_id;
    }
    public String getCredential_id() {
        return credential_id;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    public String getPassword() {
        return password;
    }
}
