package com.cenedu.backend.domain.problem.repository;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemQuestionRepository extends JpaRepository<ProblemQuestion, Long> {
}
