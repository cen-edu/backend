package com.cenedu.backend.domain.chat.repository;

import com.cenedu.backend.domain.chat.entity.ChatConceptPrereq;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 선수관계 엣지 조회. 재귀 확장은 ChatConceptRepository 가 맡고, 여기는 한 단계만 본다. */
public interface ChatConceptPrereqRepository extends JpaRepository<ChatConceptPrereq, Long> {

    /** 한 개념이 직접 요구하는 선수관계 엣지를 돌려준다. */
    List<ChatConceptPrereq> findByConceptId(Long conceptId);
}
