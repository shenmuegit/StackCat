package com.stackcat.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stackcat.entity.MethodCall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MethodCallRepository extends BaseMapper<MethodCall> {
    @Select("SELECT * FROM method_call WHERE request_trace_id = #{requestTraceId}")
    List<MethodCall> findByRequestTraceId(@Param("requestTraceId") Long requestTraceId);

    @Select("SELECT * FROM method_call WHERE request_trace_id = #{requestTraceId} ORDER BY sequence ASC")
    List<MethodCall> findByRequestTraceIdOrderBySequence(@Param("requestTraceId") Long requestTraceId);

    @Select("SELECT * FROM method_call WHERE request_trace_id = #{requestTraceId} AND parent_call_id IS NULL")
    List<MethodCall> findRootCallsByRequestTraceId(@Param("requestTraceId") Long requestTraceId);

    @Select("SELECT * FROM method_call WHERE parent_call_id = #{parentCallId} ORDER BY sequence ASC")
    List<MethodCall> findByParentCallId(@Param("parentCallId") Long parentCallId);
}

