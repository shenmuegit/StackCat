package com.stackcat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestTraceDTO {
    private Long id;
    private String requestId;
    private Long threadId;
    private String threadName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String httpHeaders;
    private LocalDateTime createdAt;
}

