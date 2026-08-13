---
name: 이미지 저장 기능
about: 문항 이미지와 학생 답안 이미지 저장 및 조회
title: "[Feature] 이미지 저장 및 조회 구현"
labels: ""
assignees: ""
---

## 작업 내용

문항 이미지와 학생 답안 이미지를 S3에 저장하고 조회할 수 있도록 구현합니다.

## API

```text
POST /api/images/problems/{questionId}
GET  /api/images/problems/{questionId}

POST /api/images/answers/{assignmentStudentId}/answer-units/{answerUnitId}
GET  /api/images/answers/{assignmentStudentId}/answer-units/{answerUnitId}
```

- `assignmentStudentId`: 어떤 학생의 어떤 학습지 수행인지 확인합니다.
- `answerUnitId`: 해당 문제의 어느 답안 칸인지 확인합니다. 빈칸형은 B1, B2처럼 구분됩니다.

## 해야 할 일

- [ ] PNG, JPEG 이미지 업로드
- [ ] 5MB 이하 파일만 허용
- [ ] 문항 이미지 저장 및 조회
- [ ] 학생 답안 이미지 저장 및 조회
- [ ] 조회할 때 만료되는 S3 URL 반환
- [ ] JWT 사용자 권한 확인
- [ ] 업로드 및 조회 테스트 작성

## 확인할 내용

- 이미지는 원본 그대로 저장합니다.
- 같은 위치에 다시 올리면 기존 이미지를 덮어씁니다.
- 학생 답안 이미지 저장 공간은 외부에 공개하지 않습니다.
- 문항 이미지의 저장 위치는 `problem_asset.storage_key`에 기록합니다.
- 답안 이미지의 저장 위치는 `answers/{assignmentStudentId}/{answerUnitId}` 규칙으로 계산하며 DB에 별도로 저장하지 않습니다.
- 만료되는 조회 URL은 DB에 저장하지 않습니다.
- 학생은 자신의 답안 이미지만 올릴 수 있습니다.
- 학생과 교사는 같은 조회 API를 사용합니다.
- 답안 이미지를 저장하기 전에 해당 답안 칸이 배정된 학습지의 문항인지 확인합니다.
