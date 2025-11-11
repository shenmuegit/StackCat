package com.stackcat.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stackcat.entity.RequestTrace;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RequestTraceRepository extends BaseMapper<RequestTrace> {
    @Select("SELECT * FROM request_trace WHERE request_id = #{requestId} ORDER BY id DESC")
    List<RequestTrace> findByRequestId(@Param("requestId") String requestId);

    @Select("SELECT * FROM request_trace WHERE request_id = #{requestId} AND start_time >= #{startTime} AND start_time <= #{endTime} ORDER BY id DESC")
    List<RequestTrace> findByRequestIdAndTimeRange(
            @Param("requestId") String requestId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM request_trace WHERE end_time IS NULL ORDER BY id DESC")
    List<RequestTrace> findActiveRequests();

    @Select("SELECT * FROM request_trace WHERE start_time >= #{startTime} AND start_time <= #{endTime} ORDER BY id DESC")
    List<RequestTrace> findByTimeRange(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
    
    @Select("SELECT * FROM request_trace ORDER BY id DESC")
    List<RequestTrace> findAllByOrderByIdDesc();
}

