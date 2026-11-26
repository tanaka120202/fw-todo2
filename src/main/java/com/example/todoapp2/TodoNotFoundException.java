package com.example.todoapp2;

public class TodoNotFoundException extends RuntimeException {
    public TodoNotFoundException() {
        super("やることが見つかりませんでした");
    }
}
