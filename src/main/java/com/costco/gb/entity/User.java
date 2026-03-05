package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_uid", nullable = false, unique = true)
    private String lineUid;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "profile_image_url", length = 512)
    private String profileImageUrl;

    @Builder.Default
    @Column(name = "has_costco_membership")
    private Boolean hasCostcoMembership = false;

    @Builder.Default
    private Integer creditScore = 100;

    @Builder.Default
    private Integer noShowCount = 0;

    @Builder.Default
    private Integer totalHostedCount = 0;

    @Builder.Default
    private Integer hostCancelCount = 0;

    @Builder.Default
    private Integer totalJoinedCount = 0;

    @Builder.Default
    private Integer participantCancelCount = 0;

    @Builder.Default
    @Column(length = 50)
    private String status = "ACTIVE";
}