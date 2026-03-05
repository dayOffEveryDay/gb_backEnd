package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_follows")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserFollow extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;
}