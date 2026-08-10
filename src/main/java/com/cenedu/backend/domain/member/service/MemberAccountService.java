package com.cenedu.backend.domain.member.service;

import java.util.Optional;

import com.cenedu.backend.domain.member.dto.result.MemberAccountAuthResult;
import com.cenedu.backend.domain.member.dto.result.MemberAccountResult;
import com.cenedu.backend.domain.member.entity.MemberAccount;
import com.cenedu.backend.domain.member.repository.MemberAccountRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회원 계정의 공개 경계. 다른 도메인은 저장소 대신 이 서비스를 사용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberAccountService {

    private final MemberAccountRepository memberAccountRepository;

    /** 로그인 아이디가 이미 등록되어 있는지 확인한다. */
    public boolean existsByLoginId(String loginId) {
        return memberAccountRepository.existsByLoginId(loginId);
    }

    /** 로그인 아이디로 인증에 필요한 활성 계정 정보를 조회한다. */
    public Optional<MemberAccountAuthResult> findActiveAccountByLoginId(String loginId) {
        return memberAccountRepository.findByLoginIdAndDeletedAtIsNull(loginId)
                .map(MemberAccountAuthResult::from);
    }

    /** 교사 계정을 생성하고 외부 공개용 계정 정보를 반환한다. */
    @Transactional
    public MemberAccountResult createTeacher(String loginId, String passwordHash, String name) {
        if (memberAccountRepository.existsByLoginId(loginId)) {
            throw new BusinessException(ErrorCode.MEMBER_LOGIN_ID_ALREADY_EXISTS);
        }

        MemberAccount teacher = MemberAccount.createTeacher(loginId, passwordHash, name);

        try {
            return MemberAccountResult.from(memberAccountRepository.saveAndFlush(teacher));
        } catch (DataIntegrityViolationException exception) {
            // 사전 중복 검사 이후 같은 아이디가 동시에 생성되는 경쟁 조건도 동일하게 처리한다.
            throw new BusinessException(ErrorCode.MEMBER_LOGIN_ID_ALREADY_EXISTS);
        }
    }
}
