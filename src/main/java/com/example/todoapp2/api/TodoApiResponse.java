package com.example.todoapp2.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TodoApiResponse<T>(boolean success, String message, T data) {
}
