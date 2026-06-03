package com.aster.app.todo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 便签待办 JSON 根对象。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record TodoDocument(List<TodoItem> items) {
    public TodoDocument {
        if (items == null) {
            items = List.of();
        }
    }
}
