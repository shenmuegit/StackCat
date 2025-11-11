package com.stackcat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MethodCallDTO {
    private Long id;
    private Long requestTraceId;
    private Long parentCallId;
    private String methodName;
    private String className;
    private String packageName;
    private LocalDateTime callTime;
    private Integer sequence;
    private Integer depth;
    private String query; // SQL/JPQL query string
    private List<MethodCallDTO> children;
}

