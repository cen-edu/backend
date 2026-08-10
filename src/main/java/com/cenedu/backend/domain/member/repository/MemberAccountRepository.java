package com.cenedu.backend.domain.member.repository;

import java.util.Optional;

import com.cenedu.backend.domain.member.entity.MemberAccount;

import org.springframework.data.jpa.repository.JpaRepository;

/** 회원 계정 영속성 저장소. 다른 도메인은 이 저장소 대신 MemberAccountService를 사용한다. */
public interface MemberAccountRepository extends JpaRepository<MemberAccount, Long> {

    /** 로그인 아이디가 이미 등록되어 있는지 확인한다. */
    boolean existsByLoginId(String loginId);

    /** 로그인 아이디로 삭제되지 않은 활성 계정을 조회한다. */
    Optional<MemberAccount> findByLoginIdAndDeletedAtIsNull(String loginId);

    /** 회원 ID로 삭제되지 않은 활성 계정을 조회한다. */
    Optional<MemberAccount> findByIdAndDeletedAtIsNull(Long id);
}
