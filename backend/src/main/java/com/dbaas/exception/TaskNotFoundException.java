package com.dbaas.exception;

/**
 * Exception thrown when a task is not found.
 */
public class TaskNotFoundException extends RuntimeException {

    public TaskNotFoundException(String taskId) {
        super("Task not found: " + taskId);
    }

    public TaskNotFoundException(String taskId, Throwable cause) {
        super("Task not found: " + taskId, cause);
    }
}
