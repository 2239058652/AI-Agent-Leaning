package com.assistant.ai.entity;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单统计 DTO — 对应 todayStats 查询结果
 */
@Data
public class OrderStats {
    private Long orderCount;
    private BigDecimal paidAmount;
    private Long paidCount;
}
