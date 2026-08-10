package com.assistant.ai.mapper;

import com.assistant.ai.entity.Order;
import com.assistant.ai.entity.OrderStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单 Mapper — MyBatis 数据访问层
 */
@Mapper
public interface OrderMapper {

    /**
     * 查询订单列表（可按状态筛选）
     */
    List<Order> listOrders(@Param("status") String status,
                           @Param("userId") String userId,
                           @Param("admin") boolean admin);

    /**
     * 根据订单号查询
     */
    Order getByOrderNo(
            @Param("orderNo") String orderNo,
            @Param("userId") String userId,
            @Param("admin") boolean admin
    );

    /**
     * 更新订单状态
     */
    int updateStatus(
            @Param("orderNo") String orderNo,
            @Param("status") String status,
            @Param("userId") String userId,
            @Param("admin") boolean admin
    );

    /**
     * 统计：今日订单数和成交额
     */
    OrderStats todayStats(
            @Param("userId") String userId,
            @Param("admin") boolean admin
    );
}
