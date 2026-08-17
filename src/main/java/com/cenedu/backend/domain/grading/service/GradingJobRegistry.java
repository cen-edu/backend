package com.cenedu.backend.domain.grading.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * 지금 자동채점이 돌고 있는 배포를 기억한다.
 *
 * <p>작업 테이블을 만들지 않는다 — 같은 상태를 두 곳에 두는 중복이고, 마이그레이션은 팀 전체 앱
 * 기동에 영향을 준다(명세 7절·12절 ①).
 *
 * <p><b>메모리 상태라 서버를 재시작하면 사라진다.</b> 데이터가 유실되는 것은 아니다 —
 * {@code grading_status}가 칸 단위로 남아 있어 다시 실행하면 남은 칸부터 이어진다. 사라지는 것은
 * "진행 중"이라는 표시뿐이다. <b>단일 인스턴스 전제이며</b>, 여러 인스턴스로 띄우면 이 판정이
 * 인스턴스마다 갈려 같은 배포가 중복 실행된다.
 */
@Component
public class GradingJobRegistry {

    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    /** 실행 자리를 잡는다. 이미 돌고 있으면 {@code false} — 검사와 등록이 한 연산이라 경합이 없다. */
    public boolean tryStart(long assignmentId) {
        return running.add(assignmentId);
    }

    public void finish(long assignmentId) {
        running.remove(assignmentId);
    }

    public boolean isRunning(long assignmentId) {
        return running.contains(assignmentId);
    }
}
