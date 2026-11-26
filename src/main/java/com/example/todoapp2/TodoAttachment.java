package com.example.todoapp2;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class TodoAttachment {
    private Long id;
    private long todoId;
    private String originalName;
    private String storageName;
    private long sizeBytes;
    private LocalDateTime createdAt;
}
