package com.stackcat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StatisticsDTO {
    private String methodName;
    private String className;
    private String packageName;
    private Long callCount;
    private Long threadCount;
    private LocalDateTime lastCallTime;
    
    // Overview fields
    private Long totalMethodCalls;
    private Long totalRequests;
    private Long activeRequests;
}

