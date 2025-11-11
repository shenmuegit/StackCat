package com.stackcat.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stackcat.entity.MethodStatistics;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface StatisticsRepository extends BaseMapper<MethodStatistics> {
    @Select("SELECT ms FROM MethodStatistics ms WHERE ms.methodName LIKE %:methodName% " +
           "AND ms.statisticsDate >= :startDate AND ms.statisticsDate <= :endDate")
    List<MethodStatistics> findByMethodNameAndDateRange(
            @Param("methodName") String methodName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Select("SELECT ms FROM MethodStatistics ms WHERE ms.statisticsDate >= :startDate " +
           "AND ms.statisticsDate <= :endDate")
    List<MethodStatistics> findByDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}

