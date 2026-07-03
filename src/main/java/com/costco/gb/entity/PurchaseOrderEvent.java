package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 託購訂單事件紀錄。
 * 每次狀態變更或異常處理均新增事件，不覆寫既有歷程。
 */
@Entity
@Table(
        name = "purchase_order_events",
        indexes = {
                @Index(name = "idx_purchase_order_events_order_time", columnList = "order_id, created_at"),
                @Index(name = "idx_purchase_order_events_actor_time", columnList = "actor_id, created_at"),
                @Index(name = "idx_purchase_order_events_type_time", columnList = "event_type, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseOrderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    // 系統自動事件沒有操作人，因此可為 null。
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(name = "from_status", length = 50)
    private String fromStatus;

    @Column(name = "to_status", length = 50)
    private String toStatus;

    @Column(length = 1000)
    private String description;

    // 保存扣分、物流單號、取消原因或缺貨資訊等補充資料。
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private Map<String, Object> metadata;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
