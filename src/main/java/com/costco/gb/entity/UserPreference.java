package com.costco.gb.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.List;

@Entity
@Table(name = "user_preferences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPreference extends BaseEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId // 與 User 共享 Primary Key
    @JoinColumn(name = "user_id")
    private User user;

    @Builder.Default
    private Boolean receiveNotifications = true;

    @Builder.Default
    @Column(length = 50)
    private String notificationMode = "ALL";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Integer> favoriteStoreIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "json")
    private List<Integer> preferredCategoryIds;
}
