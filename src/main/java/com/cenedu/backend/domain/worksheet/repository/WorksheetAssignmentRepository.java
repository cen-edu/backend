package com.cenedu.backend.domain.worksheet.repository;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetAssignmentRepository extends JpaRepository<WorksheetAssignment, Long> {
}
