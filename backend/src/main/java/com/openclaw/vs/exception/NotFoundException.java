package com.openclaw.vs.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
    
    public NotFoundException(String resource, String id) {
        super(String.format("%s 不存在: %s", resource, id));
    }
}