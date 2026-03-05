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
}