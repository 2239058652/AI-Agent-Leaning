package com.assistant.ai.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体 — 对应 orders 表
 */
@Data
public class Order {
    private Long id;
    private String orderNo;
    private String productName;
    private BigDecimal amount;
    private String status;      // PENDING / PAID / CANCELLED
    private String userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
