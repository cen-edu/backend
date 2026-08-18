# 문제 검색 VectorDB 백필

팀원이 레포지토리를 받은 뒤 문제은행 검색 인덱스를 동일하게 구성할 때 사용한다.

```bash
cp .env.example .env
# .env의 OPENAI_API_KEY에 개인 키 입력
./scripts/backfill-problem-search.sh
```

스크립트가 PostgreSQL/pgvector를 기동하고, RAG·인덱싱 기능을 켠 백엔드를 임시로 실행한 뒤
`problem_search_index_task` 큐가 안정적으로 비워질 때까지 진행률을 출력한다. Snapshot 정규화,
구조 검증, OpenAI 임베딩, pgvector 저장, retry와 멱등성은 백엔드 코드가 담당한다.

서술형 임시 문제와 구조 검증을 통과하지 못한 문제는 자동으로 제외된다. 실패 작업이 하나라도
발생하면 스크립트는 실패하고 로그를 `/tmp/cen-edu-problem-backfill.log`에 남긴다.

환경에 따라 다음 값을 덮어쓸 수 있다.

```bash
BACKFILL_POLL_SECONDS=20 \
BACKFILL_STABLE_POLLS=3 \
./scripts/backfill-problem-search.sh
```

VectorDB 행 자체는 Git에 저장하지 않는다. 새 환경에서는 동일한 문제은행과 키로 위 명령을
실행해 재생성한다.
