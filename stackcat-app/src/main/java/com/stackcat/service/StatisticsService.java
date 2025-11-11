package com.stackcat.service;

import com.stackcat.dto.BatchTrackingRequest;
import com.stackcat.dto.MethodCallDTO;
import com.stackcat.dto.RequestTraceDTO;
import com.stackcat.dto.StatisticsDTO;
import com.stackcat.entity.MethodCall;
import com.stackcat.entity.RequestTrace;
import com.stackcat.repository.MethodCallRepository;
import com.stackcat.repository.RequestTraceRepository;
import com.stackcat.repository.StatisticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatisticsService {
    private final RequestTraceRepository requestTraceRepository;
    private final MethodCallRepository methodCallRepository;
    private final StatisticsRepository statisticsRepository;

    public StatisticsService(RequestTraceRepository requestTraceRepository,
                             MethodCallRepository methodCallRepository,
                             StatisticsRepository statisticsRepository) {
        this.requestTraceRepository = requestTraceRepository;
        this.methodCallRepository = methodCallRepository;
        this.statisticsRepository = statisticsRepository;
    }

    public List<RequestTraceDTO> getActiveRequests() {
        List<RequestTrace> traces = requestTraceRepository.findActiveRequests();
        return traces.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<RequestTraceDTO> getRequestTrace(String requestId, LocalDateTime startTime, LocalDateTime endTime) {
        List<RequestTrace> traces;
        if (requestId != null && !requestId.isEmpty()) {
            if (startTime != null && endTime != null) {
                traces = requestTraceRepository.findByRequestIdAndTimeRange(requestId, startTime, endTime);
            } else {
                traces = requestTraceRepository.findByRequestId(requestId);
            }
        } else if (startTime != null && endTime != null) {
            traces = requestTraceRepository.findByTimeRange(startTime, endTime);
        } else {
            traces = requestTraceRepository.findAllByOrderByIdDesc();
        }

        return traces.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public RequestTraceDTO getRequestTraceById(Long requestTraceId) {
        RequestTrace trace = requestTraceRepository.selectById(requestTraceId);
        return trace != null ? convertToDTO(trace) : null;
    }

    public List<MethodCallDTO> getCallTree(Long requestTraceId) {
        List<MethodCall> calls = methodCallRepository.findByRequestTraceIdOrderBySequence(requestTraceId);
        return buildTree(calls);
    }

    private List<MethodCallDTO> buildTree(List<MethodCall> calls) {
        List<MethodCallDTO> rootCalls = new ArrayList<>();
        List<MethodCallDTO> allCalls = calls.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        // Build a map of parent to children
        for (MethodCallDTO call : allCalls) {
            if (call.getParentCallId() == null) {
                rootCalls.add(call);
            } else {
                // Find parent and add as child
                allCalls.stream()
                        .filter(c -> c.getId().equals(call.getParentCallId()))
                        .findFirst()
                        .ifPresent(parent -> {
                            if (parent.getChildren() == null) {
                                parent.setChildren(new ArrayList<>());
                            }
                            parent.getChildren().add(call);
                        });
            }
        }

        // Sort root calls and all children by sequence
        rootCalls.sort((a, b) -> {
            if (a.getSequence() != null && b.getSequence() != null) {
                return a.getSequence().compareTo(b.getSequence());
            }
            return 0;
        });
        
        // Sort all children recursively
        sortChildrenBySequence(rootCalls);

        return rootCalls;
    }
    
    private void sortChildrenBySequence(List<MethodCallDTO> calls) {
        if (calls == null) {
            return;
        }
        for (MethodCallDTO call : calls) {
            if (call.getChildren() != null && !call.getChildren().isEmpty()) {
                call.getChildren().sort((a, b) -> {
                    if (a.getSequence() != null && b.getSequence() != null) {
                        return a.getSequence().compareTo(b.getSequence());
                    }
                    return 0;
                });
                sortChildrenBySequence(call.getChildren());
            }
        }
    }

    public List<StatisticsDTO> getMethodStatistics(String methodName, LocalDate startDate, LocalDate endDate) {
        List<com.stackcat.entity.MethodStatistics> stats;
        if (methodName != null && !methodName.isEmpty()) {
            stats = statisticsRepository.findByMethodNameAndDateRange(methodName, startDate, endDate);
        } else if (startDate != null && endDate != null) {
            stats = statisticsRepository.findByDateRange(startDate, endDate);
        } else {
            stats = statisticsRepository.selectList(null);
        }

        return stats.stream()
                .map(this::convertToStatisticsDTO)
                .collect(Collectors.toList());
    }

    public StatisticsDTO getStatisticsOverview() {
        // This would aggregate statistics from all method calls
        // For now, return a simple overview
        StatisticsDTO overview = new StatisticsDTO();
        overview.setTotalMethodCalls(methodCallRepository.selectCount(null));
        overview.setTotalRequests(requestTraceRepository.selectCount(null));
        overview.setActiveRequests((long) requestTraceRepository.findActiveRequests().size());
        return overview;
    }

    private RequestTraceDTO convertToDTO(RequestTrace trace) {
        RequestTraceDTO dto = new RequestTraceDTO();
        dto.setId(trace.getId());
        dto.setRequestId(trace.getRequestId());
        dto.setThreadId(trace.getThreadId());
        dto.setThreadName(trace.getThreadName());
        dto.setStartTime(trace.getStartTime());
        dto.setEndTime(trace.getEndTime());
        dto.setHttpHeaders(trace.getHttpHeaders());
        dto.setCreatedAt(trace.getCreatedAt());
        return dto;
    }

    private MethodCallDTO convertToDTO(MethodCall call) {
        MethodCallDTO dto = new MethodCallDTO();
        dto.setId(call.getId());
        dto.setRequestTraceId(call.getRequestTraceId());
        dto.setParentCallId(call.getParentCallId());
        dto.setMethodName(call.getMethodName());
        dto.setClassName(call.getClassName());
        dto.setPackageName(call.getPackageName());
        dto.setCallTime(call.getCallTime());
        dto.setSequence(call.getSequence());
        dto.setDepth(call.getDepth());
        dto.setQuery(call.getQuery()); // Set SQL/JPQL query
        return dto;
    }

    private StatisticsDTO convertToStatisticsDTO(com.stackcat.entity.MethodStatistics stats) {
        StatisticsDTO dto = new StatisticsDTO();
        dto.setMethodName(stats.getMethodName());
        dto.setClassName(stats.getClassName());
        dto.setPackageName(stats.getPackageName());
        dto.setCallCount(stats.getCallCount());
        dto.setThreadCount(stats.getThreadCount());
        dto.setLastCallTime(stats.getLastCallTime());
        return dto;
    }

    @Transactional
    public void saveBatchTrackingData(List<BatchTrackingRequest> batch) {
        for (BatchTrackingRequest request : batch) {
            try {
                // Save RequestTrace
                RequestTrace trace = new RequestTrace();
                trace.setRequestId(request.getRequestId());
                trace.setThreadId(Thread.currentThread().getId());
                trace.setThreadName(Thread.currentThread().getName());
                trace.setStartTime(request.getStartTime());
                trace.setEndTime(request.getEndTime());

                // Convert headers to JSON string
                if (request.getHeaders() != null && !request.getHeaders().isEmpty()) {
                    StringBuilder sb = new StringBuilder("{");
                    boolean first = true;
                    for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                        if (!first) {
                            sb.append(",");
                        }
                        sb.append("\"").append(escapeJson(entry.getKey())).append("\":\"");
                        sb.append(escapeJson(entry.getValue())).append("\"");
                        first = false;
                    }
                    sb.append("}");
                    trace.setHttpHeaders(sb.toString());
                }

                // createdAt will be automatically filled by MyBatis-Plus MetaObjectHandler
                requestTraceRepository.insert(trace);
                Long traceId = trace.getId();

                // Save MethodCalls
                if (request.getMethodCalls() != null && !request.getMethodCalls().isEmpty()) {
                    // Map to store index -> MethodCall ID for parent lookup
                    Map<Integer, Long> indexToCallId = new HashMap<>();

                    for (int i = 0; i < request.getMethodCalls().size(); i++) {
                        BatchTrackingRequest.MethodCallInfo callInfo = request.getMethodCalls().get(i);

                        MethodCall methodCall = new MethodCall();
                        methodCall.setRequestTraceId(traceId);
                        methodCall.setClassName(callInfo.getClassName());
                        methodCall.setMethodName(callInfo.getMethodName());
                        methodCall.setPackageName(callInfo.getPackageName());
                        methodCall.setCallTime(callInfo.getCallTime());
                        methodCall.setSequence(callInfo.getSequence());
                        methodCall.setDepth(callInfo.getDepth());
                        methodCall.setQuery(callInfo.getQuery()); // Set SQL/JPQL query

                        // Set parent call id based on parentIndex
                        if (callInfo.getParentIndex() != null && callInfo.getParentIndex() >= 0) {
                            Long parentCallId = indexToCallId.get(callInfo.getParentIndex());
                            methodCall.setParentCallId(parentCallId);
                        } else {
                            methodCall.setParentCallId(null);
                        }

                        methodCallRepository.insert(methodCall);
                        indexToCallId.put(i, methodCall.getId());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}

