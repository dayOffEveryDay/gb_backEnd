package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "purchase_chat_messages", indexes =
        @Index(name = "idx_purchase_chat_messages_room_time", columnList = "room_id, created_at"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PurchaseChatMessage extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private PurchaseChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Builder.Default
    @Column(name = "message_type", nullable = false, length = 30)
    private String messageType = "TEXT";

    @Column(nullable = false, columnDefinition = "text")
    private String content;
}
