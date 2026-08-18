package com.cenedu.backend.domain.problem.service;

import java.util.UUID;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringVerificationStatus;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문제 생성 결과의 불변 스냅샷 Version을 저장하고 Session 포인터를 연결한다. */
@Service
public class ProblemAuthoringVersionService {
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;

    public ProblemAuthoringVersionService(ProblemAuthoringSessionRepository sessionRepository,
                                          ProblemAuthoringVersionRepository versionRepository) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
    }

    /** 문제은행 스냅샷을 최초 PASSED Version으로 보존한다. */
    @Transactional
    public ProblemAuthoringVersion saveBankReuse(long ownerTeacherId, long sessionId,
                                                 long sourceQuestionId, String snapshot,
                                                 String assetManifest) {
        ProblemAuthoringSession session = sessionRepository
                .findByIdAndOwnerTeacherId(sessionId, ownerTeacherId)
                .orElseThrow(() -> new IllegalArgumentException("Session을 찾을 수 없습니다."));
        UUID requestId = UUID.nameUUIDFromBytes(
                ("bank-reuse:" + sourceQuestionId + ":" + sessionId).getBytes());
        ProblemAuthoringVersion version = versionRepository.save(ProblemAuthoringVersion.create(
                sessionId, 1, null, requestId, AuthoringOperationType.BANK_REUSE,
                sourceQuestionId, 1, snapshot, assetManifest == null ? "{}" : assetManifest,
                "문제은행 문항 재사용"));
        version.startVerification(UUID.nameUUIDFromBytes(("verify:" + version.getId()).getBytes()));
        version.passVerification("BANK_REUSE");
        session.initializeCurrentVersion(version.getId());
        return version;
    }
}
