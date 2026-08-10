package com.cenedu.backend.domain.member.entity;

import java.time.Instant;

import com.cenedu.backend.global.common.enums.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 교사와 학생의 로그인 계정. */
@Entity
@Getter
@Table(name = "member_account",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_account_login_id", columnNames = "login_id"))
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private UserRole role;

    @Column(name = "login_id", nullable = false, length = 64)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    private MemberAccount(UserRole role, String loginId, String passwordHash, String name) {
        this.role = role;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
    }

    /** 교사 계정을 생성한다. */
    public static MemberAccount createTeacher(String loginId, String passwordHash, String name) {
        return new MemberAccount(UserRole.TEACHER, loginId, passwordHash, name);
    }

    /** 학생 계정을 생성한다. */
    public static MemberAccount createStudent(String loginId, String passwordHash, String name) {
        return new MemberAccount(UserRole.STUDENT, loginId, passwordHash, name);
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void delete() {
        this.deletedAt = Instant.now();
    }

    public boolean deleted() {
        return deletedAt != null;
    }
}
