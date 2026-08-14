package com.cenedu.backend.domain.worksheet.repository;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorksheetRepository extends JpaRepository<Worksheet, Long> {
}
