package com.hf.democourier.request;


/**
 * This class is to showcase how to login to the courier server.
 * The POST request body in JSON format is as follows:{"user":"user","pwd":"pwd"}.
 * @author jvtang
 * @since 2024-08-22
 */
public class AppLoginReq {
    private String user;
    private String pwd;

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }
}
