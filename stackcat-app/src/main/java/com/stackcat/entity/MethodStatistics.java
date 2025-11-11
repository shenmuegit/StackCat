package com.stackcat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("method_statistics")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MethodStatistics {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String methodName;

    private String className;

    private String packageName;

    private Long callCount;

    private Long threadCount;

    private LocalDateTime lastCallTime;

    private LocalDate statisticsDate;
}

