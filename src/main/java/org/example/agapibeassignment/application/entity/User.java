package org.example.agapibeassignment.application.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String phone;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(nullable = false) @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    @Column(name = "email_verified", nullable = false) @Builder.Default
    private boolean emailVerified = false;
    @Column(name = "phone_verified", nullable = false) @Builder.Default
    private boolean phoneVerified = false;
    @Column(nullable = false) @Enumerated(EnumType.STRING) @Builder.Default
    private Role role = Role.USER;
    @Column(nullable = false) @Builder.Default
    private String status = "ACTIVE";
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); updatedAt = LocalDateTime.now(); }
    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
