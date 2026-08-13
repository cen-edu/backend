package com.cenedu.backend.domain.worksheet.repository;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetAssignmentStudentRepository
        extends JpaRepository<WorksheetAssignmentStudent, Long> {
}
