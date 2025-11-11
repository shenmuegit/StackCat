package com.stackcat.controller;

import com.stackcat.dto.MethodCallDTO;
import com.stackcat.dto.QueryRequest;
import com.stackcat.dto.RequestTraceDTO;
import com.stackcat.dto.StatisticsDTO;
import com.stackcat.service.StatisticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;

    public ApiController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    @GetMapping("/tracking/active")
    public ResponseEntity<List<RequestTraceDTO>> getActiveRequests() {
        return ResponseEntity.ok(statisticsService.getActiveRequests());
    }

    @GetMapping("/tracking/history")
    public ResponseEntity<List<RequestTraceDTO>> getHistory(
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        List<RequestTraceDTO> traces = statisticsService.getRequestTrace(requestId, startTime, endTime);
        return ResponseEntity.ok(traces);
    }

    @GetMapping("/tracking/request/{requestId}")
    public ResponseEntity<RequestTraceDTO> getRequest(@PathVariable String requestId) {
        List<RequestTraceDTO> traces = statisticsService.getRequestTrace(requestId, null, null);
        if (traces.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(traces.get(0));
    }

    @GetMapping("/tracking/tree/{requestTraceId}")
    public ResponseEntity<List<MethodCallDTO>> getCallTree(@PathVariable Long requestTraceId) {
        List<MethodCallDTO> tree = statisticsService.getCallTree(requestTraceId);
        return ResponseEntity.ok(tree);
    }

    @GetMapping("/statistics/method")
    public ResponseEntity<List<StatisticsDTO>> getMethodStatistics(
            @RequestParam(required = false) String methodName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<StatisticsDTO> stats = statisticsService.getMethodStatistics(methodName, startDate, endDate);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/statistics/overview")
    public ResponseEntity<StatisticsDTO> getStatisticsOverview() {
        StatisticsDTO overview = statisticsService.getStatisticsOverview();
        return ResponseEntity.ok(overview);
    }

    @PostMapping("/tracking/batch")
    public ResponseEntity<String> receiveBatchTrackingData(@RequestBody List<com.stackcat.dto.BatchTrackingRequest> batch) {
        try {
            statisticsService.saveBatchTrackingData(batch);
            return ResponseEntity.ok("OK");
        } catch (Exception e) {
            System.err.println("[StackCat App] Error processing batch: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }
    }
}

