package com.stackcat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@TableName("request_trace")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestTrace {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;

    private Long threadId;

    private String threadName;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String httpHeaders;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}

