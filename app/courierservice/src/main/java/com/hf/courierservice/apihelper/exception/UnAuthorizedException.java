package com.hf.courierservice.apihelper.exception;

public class UnAuthorizedException extends Exception {
   public UnAuthorizedException()
    {
        super("Unauthorized Exception");
    }
}
