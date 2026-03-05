package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "blocks")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Block extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;
}