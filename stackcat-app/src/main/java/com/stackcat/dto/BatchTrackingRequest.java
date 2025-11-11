package com.stackcat.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class BatchTrackingRequest {
    private String requestId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Map<String, String> headers;
    private List<MethodCallInfo> methodCalls;
    
    @Data
    public static class MethodCallInfo {
        private String className;
        private String methodName;
        private String packageName;
        private Integer depth;
        private Integer sequence;
        private LocalDateTime callTime;
        private Integer parentIndex;
        private String query; // SQL/JPQL query string
    }
}

