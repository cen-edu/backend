package com.cenedu.backend.domain.member.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.cenedu.backend.domain.member.dto.request.StudentCreateRequest;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** 학생 일괄 등록용 UTF-8 CSV 파일을 읽고 모든 행을 저장 전에 검증한다. */
@Component
public class StudentBulkCsvParser {

    private static final long MAX_FILE_SIZE = 1024L * 1024L;
    private static final int MAX_STUDENT_COUNT = 500;
    private static final List<String> EXPECTED_HEADER = List.of("학생 이름", "학년");

    /** CSV 파일 전체를 파싱하고 검증된 학생 생성 요청 목록을 반환한다. */
    public List<StudentCreateRequest> parse(MultipartFile file) {
        validateFile(file);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                file.getInputStream(),
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            return parseRows(reader);
        } catch (CharacterCodingException exception) {
            throw new BusinessException(
                    ErrorCode.MEMBER_STUDENT_BULK_PARSE_FAILED,
                    "CSV 파일은 UTF-8 인코딩이어야 합니다.");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_BULK_PARSE_FAILED);
        }
    }

    /** 파일 존재 여부, 확장자, MIME 타입, 크기를 검증한다. */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_BULK_FILE_REQUIRED);
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_BULK_FILE_TOO_LARGE);
        }

        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_BULK_INVALID_FORMAT);
        }

        String contentType = file.getContentType();
        if (contentType != null
                && !contentType.equalsIgnoreCase("text/csv")
                && !contentType.equalsIgnoreCase("application/csv")
                && !contentType.equalsIgnoreCase("application/vnd.ms-excel")
                && !contentType.equalsIgnoreCase("text/plain")
                && !contentType.equalsIgnoreCase("application/octet-stream")) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_BULK_INVALID_FORMAT);
        }
    }

    /** 헤더와 데이터 행을 순서대로 읽어 검증 결과를 만든다. */
    private List<StudentCreateRequest> parseRows(BufferedReader reader) throws IOException {
        String headerLine = reader.readLine();
        if (headerLine == null) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_BULK_EMPTY);
        }

        List<String> header = parseCsvLine(removeBom(headerLine), 1);
        if (!EXPECTED_HEADER.equals(header.stream().map(String::trim).toList())) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_BULK_HEADER_INVALID);
        }

        int registrationYear = Year.now().getValue();
        List<StudentCreateRequest> requests = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        String line;
        int rowNumber = 1;
        int studentRowCount = 0;

        while ((line = reader.readLine()) != null) {
            rowNumber++;
            if (line.isBlank()) {
                continue;
            }
            studentRowCount++;
            if (studentRowCount > MAX_STUDENT_COUNT) {
                throw new BusinessException(ErrorCode.MEMBER_STUDENT_BULK_TOO_MANY);
            }

            List<String> columns;
            try {
                columns = parseCsvLine(line, rowNumber);
            } catch (BusinessException exception) {
                errors.add(exception.getMessage());
                continue;
            }
            validateRow(columns, rowNumber, registrationYear, requests, errors);
        }

        if (requests.isEmpty() && errors.isEmpty()) {
            throw new BusinessException(ErrorCode.MEMBER_STUDENT_BULK_EMPTY);
        }
        if (!errors.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.MEMBER_STUDENT_BULK_VALIDATION_FAILED,
                    String.join(" ", errors));
        }
        return List.copyOf(requests);
    }

    /** 한 데이터 행의 열 개수, 이름, 학년, 파일 내 중복 여부를 검증한다. */
    private void validateRow(
            List<String> columns,
            int rowNumber,
            int registrationYear,
            List<StudentCreateRequest> requests,
            List<String> errors
    ) {
        if (columns.size() != 2) {
            errors.add(rowNumber + "행: 학생 이름과 학년 두 칸을 입력해 주세요.");
            return;
        }

        String name = columns.get(0).trim();
        String gradeValue = columns.get(1).trim();
        boolean valid = true;
        if (name.length() < 2 || name.length() > 50) {
            errors.add(rowNumber + "행 name: 이름은 2자 이상 50자 이하여야 합니다.");
            valid = false;
        }

        int grade = 0;
        try {
            grade = Integer.parseInt(gradeValue);
            if (grade < 1 || grade > 3) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            errors.add(rowNumber + "행 grade: 학년은 1, 2, 3 중 하나여야 합니다.");
            valid = false;
        }

        if (!valid) {
            return;
        }

        requests.add(new StudentCreateRequest(name, registrationYear, grade));
    }

    /** 따옴표와 쉼표 이스케이프를 지원해 CSV 한 줄을 셀 목록으로 변환한다. */
    private List<String> parseCsvLine(String line, int rowNumber) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;

        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }

        if (quoted) {
            throw new BusinessException(
                    ErrorCode.MEMBER_STUDENT_BULK_VALIDATION_FAILED,
                    rowNumber + "행: 따옴표가 올바르게 닫히지 않았습니다.");
        }
        values.add(value.toString());
        return values;
    }

    /** UTF-8 BOM이 있으면 첫 헤더 앞에서 제거한다. */
    private String removeBom(String value) {
        return value.startsWith("\uFEFF") ? value.substring(1) : value;
    }
}
