package com.uniuni.SysMgrTool.Response;

public class LoginResponse extends ResponseBase{
    public LoginResponseData getData() {
        return data;
    }

    public void setData(LoginResponseData data) {
        this.data = data;
    }

    private LoginResponseData data;
}
