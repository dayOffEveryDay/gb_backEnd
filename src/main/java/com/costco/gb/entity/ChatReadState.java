package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_read_states", uniqueConstraints =
        @UniqueConstraint(name = "uk_chat_read_states_campaign_user", columnNames = {"campaign_id", "user_id"}))
@Getter @Setter @NoArgsConstructor
public class ChatReadState extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "campaign_id", nullable = false)
    private Long campaignId;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;
    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;
}
