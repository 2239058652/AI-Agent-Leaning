package com.assistant.ai.service;

import com.assistant.ai.entity.Order;
import com.assistant.ai.entity.OrderStats;
import com.assistant.ai.mapper.OrderMapper;
import com.assistant.ai.security.AgentAuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 订单服务 — 业务逻辑层
 * <p>
 * 为 Agent 工具提供业务操作：
 * - queryOrders: 只读查询
 * - analyzeOrders: 聚合分析
 * - cancelOrder: 敏感写操作
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;

    /**
     * 查询订单列表
     *
     * @param status 状态筛选，可为 null（查全部）
     * @return 订单列表
     */
    public List<Order> queryOrders(String status, AgentAuthContext authContext) {
        return orderMapper.listOrders(
                status,
                authContext.userId(),
                authContext.hasRole("ADMIN")
        );
    }

    /**
     * 今日订单统计
     *
     * @return OrderStats 包含 orderCount、paidAmount、paidCount
     */
    public OrderStats todayStats(AgentAuthContext authContext) {
        return orderMapper.todayStats(
                authContext.userId(),
                authContext.hasRole("ADMIN")
        );
    }

    public boolean canAccessOrder(
            String orderNo,
            AgentAuthContext authContext
    ) {
        boolean admin = authContext.hasRole("ADMIN");

        return orderMapper.getByOrderNo(
                orderNo,
                authContext.userId(),
                admin
        ) != null;
    }

    /**
     * 取消订单 — 敏感写操作
     * <p>
     * 只有 PENDING 状态的订单可以取消。
     *
     * @param orderNo 订单号
     * @return 操作结果消息
     */
    public String cancelOrder(String orderNo, AgentAuthContext authContext) {
        boolean admin = authContext.hasRole("ADMIN");

        Order order = orderMapper.getByOrderNo(
                orderNo,
                authContext.userId(),
                admin
        );
        if (order == null) {
            return "订单不存在: " + orderNo;
        }
        if ("CANCELLED".equals(order.getStatus())) {
            return "订单已取消，无需重复操作: " + orderNo;
        }
        if (!"PENDING".equals(order.getStatus())) {
            return "只有待支付订单可以取消，当前状态: " + order.getStatus();
        }

        int rows = orderMapper.updateStatus(orderNo, "CANCELLED", authContext.userId(), admin);
        if (rows > 0) {
            log.info("订单已取消: {}", orderNo);
            return "订单取消成功: " + orderNo + "（" + order.getProductName() + "，¥" + order.getAmount() + "）";
        }
        return "取消失败，请稍后重试";
    }
}
