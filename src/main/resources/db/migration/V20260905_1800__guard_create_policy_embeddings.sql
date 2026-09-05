CREATE EXTENSION IF NOT EXISTS vector;

-- 파일/모델 버전별 행을 보관하고 현재 배포의 version_key만 검색한다.
CREATE TABLE guard_policy_embedding (
    version_key varchar(64) PRIMARY KEY,
    policy_id varchar(80) NOT NULL,
    scope varchar(20) NOT NULL CHECK (scope IN ('COMMON', 'SOLVE_CHAT', 'REVIEW_CHAT')),
    effect varchar(5) NOT NULL CHECK (effect IN ('ALLOW', 'BLOCK')),
    message text NOT NULL,
    body text NOT NULL,
    content_hash varchar(64) NOT NULL,
    embedding_model varchar(200) NOT NULL,
    embedding vector(1024) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX guard_policy_embedding_scope_model_idx ON guard_policy_embedding (scope, embedding_model);
-- 소규모 정책은 정확 검색을 사용한다. ANN 인덱스는 규모/recall 측정 후 도입한다.
