package com.hf.courierservice.apihelper.exception;

public class TooMuchRequestException extends Exception{
    public TooMuchRequestException() {
        super("Too much request");
    }
}

