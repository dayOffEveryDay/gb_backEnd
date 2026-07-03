package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_chat_rooms", uniqueConstraints =
        @UniqueConstraint(name = "uk_purchase_chat_rooms_order", columnNames = "order_id"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseChatRoom extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private PurchaseOrder purchaseOrder;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";
}
