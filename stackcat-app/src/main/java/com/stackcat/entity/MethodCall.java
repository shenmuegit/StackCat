package com.stackcat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("method_call")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MethodCall {
    @TableId(type = IdType.AUTO)
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
}

