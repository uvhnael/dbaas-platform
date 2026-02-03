package com.dbaas.controller;

import com.dbaas.exception.TaskNotFoundException;
import com.dbaas.model.Task;
import com.dbaas.model.dto.ApiResponse;
import com.dbaas.repository.TaskRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for task management.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task Management API")
public class TaskController {

    private final TaskRepository taskRepository;

    @GetMapping("/clusters/{clusterId}/tasks")
    @Operation(summary = "List all tasks for a cluster")
    public ResponseEntity<ApiResponse<List<Task>>> listClusterTasks(@PathVariable String clusterId) {
        List<Task> tasks = taskRepository.findByClusterIdOrderByCreatedAtDesc(clusterId);
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/tasks")
    @Operation(summary = "List all tasks")
    public ResponseEntity<ApiResponse<List<Task>>> listAllTasks() {
        List<Task> tasks = taskRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/tasks/{taskId}")
    @Operation(summary = "Get task details")
    public ResponseEntity<ApiResponse<Task>> getTask(@PathVariable String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));
        return ResponseEntity.ok(ApiResponse.success(task));
    }
}
