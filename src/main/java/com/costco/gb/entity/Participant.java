package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "participants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Participant extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Integer quantity;

    @Builder.Default
    @Column(length = 50)
    private String status = "JOINED";

    @Column(name = "dispute_reason", length = 500)
    // 參與者提出爭議或申訴時填寫的原因
    private String disputeReason;
    // 🌟 團主的矛：團主標記棄單時的備註 (例如："打電話都不接")
    @Column(name = "host_note", length = 255)
    private String hostNote;
}
