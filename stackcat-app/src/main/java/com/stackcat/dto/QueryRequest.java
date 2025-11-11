package com.stackcat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class QueryRequest {
    private String requestId;
    private String methodName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDate startDate;
    private LocalDate endDate;
}

