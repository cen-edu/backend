# AWS 배포 아키텍처

강의 교안(`AWS기반의프로젝트배포강의교안`)의 EC2 + Docker + Docker Hub + VPC 구성을 이
저장소(Spring Boot 4 / Java 21 / PostgreSQL 17 + pgvector / Flyway / S3 / OpenAI)에 맞춘
배포 설계다. 실행에 필요한 파일은 모두 저장소에 들어 있다.

**백엔드 저장소** (이 저장소)

| 파일 | 역할 |
|---|---|
| [Dockerfile](../../Dockerfile) | 배포 이미지 빌드(멀티스테이지, JRE 21, 비 root) |
| [deploy/docker-compose.prod.yml](../../deploy/docker-compose.prod.yml) | EC2 에서 띄우는 3개 컨테이너 |
| [deploy/.env.prod.example](../../deploy/.env.prod.example) | EC2 `~/app/.env` 의 원본 |
| [deploy/deploy.sh](../../deploy/deploy.sh) | EC2 에서 태그 교체 배포 + 롤백 안내 |
| [application-prod.yaml](../../src/main/resources/application-prod.yaml) | 운영에서만 달라지는 정책 |

**프론트 저장소** ([cen-edu/frontend](https://github.com/cen-edu/frontend))

| 파일 | 역할 |
|---|---|
| `Dockerfile` | Vite 빌드 → nginx 서빙 (2단계) |
| `deploy/nginx.conf` | **서비스의 유일한 입구.** 정적 서빙 + `/api` 프록시 + SPA 폴백 |
| `.dockerignore` | `node_modules` / `dist` 제외 |

---

## 1. 구성 결정

- **EC2 1대 + docker compose.** 교안은 EC2 4대(react / spring / fastapi / postgres)를
  퍼블릭·프라이빗 서브넷에 나눠 두지만, 그 구성은 NAT Gateway 가 필수고 NAT 는 프리티어가
  아니다(시간당 + 데이터 처리량 과금). 이 프로젝트는 백엔드 한 덩어리라 한 대에 묶는 편이
  배포·롤백이 단순하고 프리티어 안에서 끝난다.
- **DB 는 컨테이너.** RDS 는 프리티어를 넘기면 바로 과금되고, pgvector 확장을 파라미터
  그룹에서 따로 열어야 한다. 여기서는 `pgvector/pgvector:pg17` 이미지를 그대로 쓴다 —
  로컬 [compose.yaml](../../compose.yaml) 과 같은 이미지라 재현이 쉽다.
- **이미지는 Docker Hub 경유.** t3.micro 는 메모리 1GB 라 EC2 에서 Gradle 빌드를 돌리면
  OOM 으로 죽는다. 빌드는 로컬(또는 CI), EC2 는 `pull` 만 한다.
- **프론트 컨테이너의 nginx 가 입구를 겸한다**(교안의 `reactedu` 와 같은 형태). 정적 파일을
  서빙하면서 `/api` 를 백엔드로 넘긴다. 프론트의 `VITE_API_BASE_URL` 기본값이 `/api`
  라 브라우저가 보는 오리진이 하나로 합쳐지고, **CORS 설정이 아예 필요 없어진다**
  (`CORS_ALLOWED_ORIGINS` 를 비워 둔다). 프론트 저장소에 `vercel.json` 이 있어 Vercel 배포도
  가능하지만, 그 경우 오리진이 갈라져 CORS 허용 목록과 HTTPS↔HTTP 혼합 콘텐츠 문제를
  따로 처리해야 한다.

- **HTTPS 는 CloudFront 로 붙인다.** 도메인 없이 `*.cloudfront.net` 기본 인증서로 즉시
  HTTPS 가 되고, 프론트가 `/api` 상대 경로를 쓰므로 이미지 재빌드가 필요 없다. 다만
  **CloudFront ↔ EC2 구간은 여전히 HTTP** 다 — 브라우저부터 엣지까지만 암호화된다.
  그래서 **실제 학생의 이름·답안을 넣지 않는 것이 여전히 전제 조건이다**(3.6, 6절).

이 전제들 위에서 나머지가 정해진다. 웹/DB 분리가 필요해지면 7절을 본다.

### 1.1 검토했지만 넣지 않은 것

시연용이라는 목적에 견줘 부담이 이득보다 커서 뺐다. 나중에 실서비스로 가면 이 순서로 다시 본다.

| 안 | 뺀 이유 |
|---|---|
| ECR 로 이미지 비공개화 | 두 저장소가 이미 public 이라 이미지를 감춰도 코드가 공개다. 교안이 쓰는 Docker Hub 를 유지하는 편이 절차도 짧다 |
| SSM Session Manager 로 SSH 대체 | 교안의 SSH·키 관리 실습이 학습 내용이다. 22번을 본인 IP 로 제한하면 시연 규모에서는 충분하다 |
| GitHub Actions 자동 빌드 | 배포가 드물다. 시크릿 등록과 워크플로 유지 비용이 로컬 `docker build` 두 줄보다 크다 |
| DB 백업 cron + S3 수명주기 | 시연 데이터는 다시 만들 수 있다. 시연 전 수동 덤프(5.1)로 충분하다 |

---

## 2. 구성도

```
                          인터넷
                             |
                        [  IGW  ]
                             |
+============================|====================== VPC 10.0.0.0/16 =+
|  public subnet 10.0.1.0/24 (ap-northeast-2a)                        |
|  +--------------------------|-------------------------------------+ |
|  |  EC2 t3.micro (Amazon Linux 2023) + Elastic IP                  | |
|  |                      :80 |                                      | |
|  |                +---------v----------+                           | |
|  |                |     frontend       |  /      React 정적 파일    | |
|  |                |  (nginx = 입구)    |                           | |
|  |                +---------+----------+                           | |
|  |                          | /api                                 | |
|  |                    +-----v-----+  :8080   +----------------+    | |
|  |                    |  backend  |--------->|    postgres    |    | |
|  |                    | (비공개)   |          |   + pgvector   |    | |
|  |                    +-----+-----+          +-------+--------+    | |
|  |                          |   docker network: cen-edu-net |      | |
|  |                          |                       volume: pg-data| |
|  +--------------------------|-------------------------------------+ |
+============================ | =====================================+
                              |
             +----------------+----------------+
             v                v                v
      S3 (문항/답안)      OpenAI API      Docker Hub (pull)
```

- 외부로 열리는 포트는 **80 하나**다. 백엔드(8080)와 DB(5432)는 호스트 포트를 쓰지 않아
  보안 그룹이 잘못 열려도 그 컨테이너까지 닿지 않는다.
- 라우팅 규칙은 프론트 이미지 안의 `deploy/nginx.conf` 에 있다. **라우팅을 바꾸려면 프론트
  이미지를 다시 빌드해야 한다** — 대신 컨테이너가 하나 줄고 구조가 교안과 같아진다.
- 컨테이너끼리는 서비스 이름(`postgres`, `backend`)으로 통신한다. 교안의 다중 EC2 구성처럼
  사설 IP 를 박지 않으므로 인스턴스를 새로 만들어도 설정이 그대로다.

---

## 3. AWS 리소스

### 3.1 네트워크

VPC 콘솔의 **"VPC 등" 마법사**로 한 번에 만든다(교안 마지막 절과 같다). NAT 게이트웨이는
**없음**을 고른다.

| 항목 | 값 |
|---|---|
| VPC | `cen-edu-vpc`, CIDR `10.0.0.0/16` |
| 가용 영역 | 1개 (ap-northeast-2a) |
| 퍼블릭 서브넷 | 1개 `10.0.1.0/24` |
| 프라이빗 서브넷 | 0개 (NAT 비용 때문. 7절 참고) |
| 인터넷 게이트웨이 | 1개, VPC 에 연결 |
| 라우팅 테이블 | `10.0.0.0/16 → local`, `0.0.0.0/0 → IGW` |

### 3.2 보안 그룹

| 이름 | 인바운드 | 설명 |
|---|---|---|
| `cen-edu-web-sg` | TCP 80 ← `0.0.0.0/0` | 프론트 컨테이너의 nginx. 서비스 입구 |
| | TCP 22 ← `<내 IP>/32` | SSH. **`0.0.0.0/0` 으로 두지 않는다** — 열어 두면 곧바로 자동 스캔이 붙는다 |

아웃바운드는 기본값(전체 허용)을 그대로 둔다. Docker Hub `pull`, OpenAI 호출, S3 접근이
모두 아웃바운드다. DB·앱용 보안 그룹은 필요 없다 — 두 컨테이너가 호스트 포트를 쓰지 않아
보안 그룹의 대상이 아니다.

### 3.3 EC2

| 항목 | 값 |
|---|---|
| AMI | Amazon Linux 2023 |
| 인스턴스 | **t3.micro (프리티어)**. 컨테이너별 메모리 상한(4.2절)을 걸면 1GB 안에서 돈다 |
| 스토리지 | gp3 20GB (기본 8GB 는 이미지 몇 개만 쌓여도 찬다) |
| 키 페어 | `cen-edu.pem` (PuTTY 를 쓰면 `.ppk`) |
| 네트워크 | 위 VPC / 퍼블릭 서브넷, 퍼블릭 IP 자동 할당 |
| 탄력적 IP | 할당 후 연결. **없으면 인스턴스를 껐다 켤 때마다 IP 가 바뀌어** 프론트 설정과 CORS 를 매번 고쳐야 한다 |
| IAM 역할 | 3.4 참고 |

### 3.4 S3 와 IAM

문항·답안 이미지가 S3 에 올라간다(`app.storage.s3`). 버킷 2개를 만든다.

| 버킷 | 용도 |
|---|---|
| `cen-edu-problem-<식별자>` | 문항 이미지 |
| `cen-edu-answer-<식별자>` | 학생 답안 이미지 |

두 버킷 모두 **퍼블릭 액세스 차단을 켠 채로 둔다.** 앱은 presigned URL 로 읽기를 내주므로
버킷을 공개할 이유가 없다. 학생 필기가 담긴 답안 버킷이 공개되면 그대로 유출이다.

권한은 **전용 IAM 사용자**를 만들어 액세스 키를 발급하고 `.env` 에 넣는다. 현재 코드
(`S3Config`)가 `app.storage.s3.access-key-id` / `secret-access-key` 를 정적 자격증명으로
바로 쓰고, 두 값이 `@NotBlank` 라 비우면 S3 를 켠 채로는 기동하지 않는다.

> EC2 IAM 역할(인스턴스 메타데이터의 임시 자격증명)을 쓰려면 `S3Config` 가 키가 비었을 때
> `DefaultCredentialsProvider` 로 넘어가도록 고쳐야 한다. 장기 키가 파일로 남지 않고 회수도
> 역할만 떼면 끝나므로, 실제 학생 데이터를 넣기 전에 바꿔 두는 편이 좋다. 이 문서의 구성은
> 코드를 건드리지 않는 쪽(액세스 키)으로 맞춰 뒀다.

사용자(또는 역할)에 붙일 정책(두 버킷에만, 필요한 동작만):

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
    "Resource": [
      "arn:aws:s3:::cen-edu-problem-<식별자>/*",
      "arn:aws:s3:::cen-edu-answer-<식별자>/*"
    ]
  }]
}
```

### 3.5 CloudFront (HTTPS)

도메인을 사지 않고 HTTPS 를 얻는 가장 싼 경로다. 로그인 폼이 있는 HTTP 페이지는 브라우저가
"안전하지 않음" 을 띄우는데, 시연 화면에 그 표시가 그대로 보인다.

| 설정 | 값 | 이유 |
|---|---|---|
| 오리진 | EC2 퍼블릭 DNS (`ec2-*.compute.amazonaws.com`) | CloudFront 는 IP 를 오리진으로 받지 않는다 |
| 오리진 프로토콜 | **HTTP 만** | EC2 에 인증서가 없다 |
| 뷰어 프로토콜 | Redirect HTTP to HTTPS | http 로 들어와도 https 로 넘긴다 |
| 캐시 정책 | **CachingDisabled** | API 응답이 캐시돼 다른 사용자에게 새는 사고를 막는다 |
| 오리진 요청 정책 | **AllViewer** | `Authorization` 헤더(JWT)를 백엔드까지 전달한다 |
| 허용 메서드 | GET~DELETE 전체 | 로그인·제출·채점이 GET 이 아니다 |
| 응답 타임아웃 | 30초 → **60초** | 보고서 PDF 렌더링 같은 동기 경로 대비 |
| WAF | 없음 | 월 $14 수준이라 시연용에는 과하다 |

전파에 5~10분 걸린다. 확인:

```bash
curl -s https://<배포도메인>/actuator/health
curl -s -o /dev/null -w '%{http_code} -> %{redirect_url}
' http://<배포도메인>/
```

**주의 — 인스턴스를 중지했다 켜면 퍼블릭 DNS 가 바뀐다.** 그러면 CloudFront 오리진도
같이 고쳐야 한다. 시연 전에 EC2 를 재시작했다면 이 점을 먼저 확인한다.

### 3.6 남는 한계

- CloudFront ↔ EC2 구간은 평문이다. 조이려면 보안 그룹의 80 번을 CloudFront 관리형 접두사
  목록(`com.amazonaws.global.cloudfront.origin-facing`)으로 좁혀 EC2 직접 접근을 막는다.
- 그래도 오리진 구간 자체는 HTTP 다. 실데이터를 다루려면 EC2 에 인증서를 두거나(도메인 필요)
  VPC 오리진으로 바꿔야 한다.

### 3.7 청구 알람

교안 4절대로 **결제 알람을 먼저 건다.** 리전을 `us-east-1` 로 바꾼 뒤 CloudWatch 청구
지표에서 `EstimatedCharges > 5 USD` 알람을 만들고 이메일을 구독한다. 연결되지 않은 EIP,
지우지 않은 볼륨처럼 조용히 새는 항목이 여기서 먼저 잡힌다.

---

## 4. 구축 절차

### 4.1 EC2 준비

```bash
sudo timedatectl set-timezone Asia/Seoul
sudo dnf upgrade -y
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker $USER
exec newgrp docker
docker --version
mkdir -p ~/app
```

`docker compose` 플러그인이 없으면 설치한다:

```bash
docker compose version || {
  mkdir -p ~/.docker/cli-plugins
  curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 -o ~/.docker/cli-plugins/docker-compose
  chmod +x ~/.docker/cli-plugins/docker-compose
}
```

### 4.2 메모리 — 컨테이너 상한과 스왑

1GB 안에 컨테이너 4개가 들어간다. 그냥 띄우면 안 되고 **상한을 걸어야 한다.**
`JAVA_OPTS` 의 `MaxRAMPercentage=75` 는 컨테이너에 상한이 없으면 호스트 전체(1GB)를
기준으로 잡아 힙만 768MB 를 노린다. 그러면 PostgreSQL 과 부딪혀 커널이 자바를 죽이고,
증상이 "며칠 뒤 앱만 조용히 사라짐"이라 원인을 찾기 어렵다.

`docker-compose.prod.yml` 에 상한이 이미 들어 있다. 실측값(상한을 건 상태, 기동 직후 +
가벼운 요청):

실제 t3.micro 에 띄워 잰 값이다(총 메모리 913MB). 처음에는 backend 512m / postgres 256m
으로 올렸는데, 컨테이너 합계 530MB + OS·도커 데몬 116MB 가 물리 메모리를 넘겨 **스왑
410MB** 를 썼다. 그래서 상한을 아래로 낮췄다.

| 컨테이너 | 512m/75% | 384m/75% | 최종 |
|---|---|---|---|
| backend | 408MB (80%) | 369MB (**96%**) | **상한 640m + 힙 55%(352MB)** |
| postgres | 119MB | 40MB | **192m** + shared_buffers 64MB |
| frontend (nginx) | 2.5MB | 4.9MB | **32m** |
| 시스템 스왑 사용 | 410MB | 138MB | — |

**상한과 힙을 분리한 이유.** 이미지 기본값은 `MaxRAMPercentage=75` 라 힙이 상한에 묶여
있다. 그래서 천장을 올리면(512m) 평상시 사용량도 같이 올라 스왑을 쓰고, 내리면(384m)
상한에 붙어 OOM 여지가 생겼다. 상한은 순간 급증을 받아내는 안전장치로 넉넉히(640m),
힙은 별도로 낮게(55% = 352MB) 두면 둘 다 해결된다. **기능이 늘어 사용량이 커져도
compose 를 다시 만질 일이 줄어드는 것이 이 구성의 목적이다.**

상한에서 죽으면 `restart: always` 가 되살리지만 그동안 화면이 멈춘다.

### 언제 t3.small 로 올리는가

1GB 안에서 늘릴 수 있는 여유에는 끝이 있다. 아래 중 하나가 보이면 인스턴스를 키운다
(중지 → 인스턴스 유형 변경 → 시작, EBS 와 데이터는 유지된다).

- 평상시 `free -h` 의 스왑 사용이 300MB 를 넘어 계속 유지될 때
- backend 실사용이 500MB 를 넘길 때 (힙 비율을 올려야 하는 시점)
- 기동 시간이 눈에 띄게 늘거나 첫 응답이 매번 느릴 때

스왑 2GB 는 그대로 둔다 — PDF 렌더링처럼 순간적으로 튀는 경로가 있고, 스왑이 있으면
그 순간에 프로세스가 죽는 대신 느려진다. 다만 **평상시 스왑 사용량이 0 에 가까운지**
가끔 `free -h` 로 확인한다. 늘 몇백 MB 를 쓰고 있으면 상한이 여전히 큰 것이다.

```bash
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

### 4.3 이미지 빌드와 push (로컬 PowerShell)

백엔드 저장소에서:

```powershell
docker login
docker build -t $env:DOCKERHUB_USER/cen-edu-backend:1.0.0 .
docker push $env:DOCKERHUB_USER/cen-edu-backend:1.0.0
```

프론트 저장소에서:

```powershell
docker build -t $env:DOCKERHUB_USER/cen-edu-frontend:1.0.0 `
  --build-arg VITE_MYSCRIPT_APPLICATION_KEY=$env:MYSCRIPT_APP_KEY `
  --build-arg VITE_MYSCRIPT_HMAC_KEY=$env:MYSCRIPT_HMAC_KEY .
docker push $env:DOCKERHUB_USER/cen-edu-frontend:1.0.0
```

**Vite 의 `VITE_` 값은 빌드 시점에 번들에 박힌다.** EC2 의 `.env` 를 고쳐도 바뀌지 않으니,
값이 달라지면 이미지를 다시 빌드해서 새 태그로 올린다. `VITE_API_BASE_URL` 은 기본값
`/api` 를 그대로 쓰면 되고, `VITE_API_TIMEOUT_MS` 는 배포 이미지에서 60초로 올려 뒀다 —
채점·문항 생성은 비동기 잡이라 요청이 짧지만, 보고서 PDF 렌더링처럼 동기로 도는 경로가
개발 기본값 10초를 넘길 수 있다.

`latest` 를 쓰지 않는다. EC2 에서 무엇이 돌고 있는지 구분되지 않고, 되돌릴 이전 태그도
남지 않는다. 태그는 배포할 때마다 올린다(`1.0.0` → `1.0.1`).

### 4.4 설정 파일 전송 (로컬)

```powershell
scp -i cen-edu.pem deploy/docker-compose.prod.yml ec2-user@<EIP>:/home/ec2-user/app/
scp -i cen-edu.pem deploy/deploy.sh               ec2-user@<EIP>:/home/ec2-user/app/
scp -i cen-edu.pem deploy/.env.prod.example       ec2-user@<EIP>:/home/ec2-user/app/.env
```

EC2 에서 `.env` 를 채우고 권한을 좁힌다. 이 파일 하나에 DB 비밀번호, JWT 서명 키,
OpenAI 키가 모두 들어 있다.

```bash
vi ~/app/.env
chmod 600 ~/app/.env
chmod +x ~/app/deploy.sh
```

### 4.5 기동

```bash
cd ~/app
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f backend
```

Flyway 가 기동할 때 스키마를 올린다(`vector` 확장 생성 포함 —
`B20260814_1500__current_database_baseline.sql`). 로그에 마이그레이션 목록이 찍히고
`Started BackendApplication` 이 나오면 끝이다.

브라우저에서 `http://<EIP>/actuator/health` 가 `{"status":"UP"}` 이면 배포가 끝났다.

---

## 5. 개발 중 재배포

**EC2 는 GitHub 를 보지 않는다.** Docker Hub 의 이미지 태그 하나만 바라본다. `main` 에
커밋해도, 심지어 EC2 에서 `git pull` 을 해도 서버는 바뀌지 않는다. 바뀌는 경로는 오직
**빌드 → push → 태그 교체** 뿐이다.

### 5.1 한 사이클 (코드 수정 → 서버 반영)

**릴리스 스크립트를 쓰면 아래 (1)~(3) 이 한 줄이다.** 빌드 → push → EC2 배포 → 헬스체크를
이어서 하고, 이미 올라간 태그면 빌드 전에 막는다.

```powershell
git fetch origin; git merge origin/main     # 팀원 코드 먼저 받는다
.\scriptselease.ps1 1.0.3                 # 백엔드만
.\scriptselease.ps1 1.0.3 -Frontend       # 백엔드 + 프론트
.\scriptselease.ps1 1.0.3 -FrontendOnly   # 프론트만
```

주소나 경로가 바뀌면 스크립트를 고치지 않고 환경 변수로 덮는다 — `CEN_EDU_HOST`,
`CEN_EDU_KEY`, `CEN_EDU_FRONTEND_REPO`, `CEN_EDU_HEALTH_URL`, `DOCKERHUB_USER`.

**`.env` 만 고쳤을 때는 스크립트를 쓸 수 없다.** 이미지 태그가 그대로라 갈아끼울 것이
없기 때문이다. EC2 에서 직접 한다:

```bash
cd ~/app && vi .env && docker compose -f docker-compose.prod.yml up -d --force-recreate backend
```

**스키마가 바뀌는 배포(마이그레이션 추가) 전에는 덤프를 먼저 뜬다**(5.4).

아래는 스크립트가 대신 해 주는 일이다. 손으로 할 때 참고한다.

**(1) 로컬에서 빌드·푸시.** 백엔드 저장소에서:

```powershell
docker build -t $env:DOCKERHUB_USER/cen-edu-backend:1.0.1 .
docker push $env:DOCKERHUB_USER/cen-edu-backend:1.0.1
```

프론트가 바뀌었으면 프론트 저장소에서:

```powershell
docker build -t $env:DOCKERHUB_USER/cen-edu-frontend:1.0.1 `
  --build-arg VITE_MYSCRIPT_APPLICATION_KEY=$env:MYSCRIPT_APP_KEY `
  --build-arg VITE_MYSCRIPT_HMAC_KEY=$env:MYSCRIPT_HMAC_KEY .
docker push $env:DOCKERHUB_USER/cen-edu-frontend:1.0.1
```

**(2) EC2 에서 태그 교체.**

```bash
cd ~/app
./deploy.sh 1.0.1            # 백엔드만
./deploy.sh 1.0.1 1.0.1      # 백엔드 + 프론트
./deploy.sh - 1.0.1          # 프론트만
```

**(3) 확인.**

```bash
docker compose -f docker-compose.prod.yml ps
curl -s http://localhost/actuator/health; echo
```

기동에 40~60초가 걸린다. 그 사이 브라우저에 502 가 뜨는 것은 정상이다 — nginx 가 아직
안 뜬 백엔드를 찾는 것이다.

### 5.2 지키면 덜 헤매는 것

- **태그를 매번 올린다**(`1.0.1` → `1.0.2`). 같은 태그를 덮어쓰면 EC2 가 이미 받아 둔
  이미지를 그대로 써서 "분명 고쳤는데 안 바뀐다" 가 된다. `latest` 를 쓰지 않는 이유도 같다.
- **프론트의 `VITE_` 값은 이미지에 박힌다.** API 주소·타임아웃·MyScript 키를 바꾸려면
  `.env` 가 아니라 이미지를 다시 빌드해야 한다.
- **라우팅(`/api` 프록시, 업로드 상한, Swagger 차단)도 프론트 이미지 안에 있다.** 그쪽을
  고쳤으면 프론트를 다시 빌드한다.
- **DB 스키마는 자동이다.** 새 백엔드가 뜰 때 Flyway 가 미적용 마이그레이션을 올린다.
  볼륨이 유지되므로 데이터는 남는다.
- **`.env` 만 고쳤을 때는** 이미지 태그가 그대로라 `deploy.sh` 를 쓸 수 없다. 이렇게 한다:
  `docker compose -f docker-compose.prod.yml up -d --force-recreate backend`

### 5.3 롤백

`deploy.sh` 는 `.env` 의 `APP_TAG` / `FRONTEND_TAG` 를 바꾸고 `pull` → `up -d` 한 뒤 컨테이너 헬스체크가
`healthy` 가 될 때까지 기다린다. 실패하면 로그를 찍고 되돌리는 명령을 알려 준다.

```bash
cp .env.bak .env && docker compose -f docker-compose.prod.yml up -d
```

**단, 스키마를 바꾼 배포는 이미지 롤백만으로 되돌아가지 않는다.** Flyway 마이그레이션은
이미 적용된 상태이고, 이전 이미지의 `ddl-auto: validate` 가 스키마 불일치로 기동을 세울 수
있다. 컬럼 삭제·타입 변경이 포함된 배포는 반드시 아래 백업을 먼저 뜬다.

### 5.4 DB 백업

배포 전과 매일 한 번:

```bash
mkdir -p ~/backup
docker exec cen-edu-postgres pg_dump -U cen cen_edu | gzip > ~/backup/cen_edu_$(date +%F).sql.gz
```

`~/backup` 은 같은 EBS 볼륨이라 인스턴스가 통째로 날아가면 함께 없어진다. 실제 운영으로
가면 S3 로 올리거나 EBS 스냅샷 일정을 잡는다.

복원:

```bash
gunzip -c ~/backup/cen_edu_2026-08-20.sql.gz | docker exec -i cen-edu-postgres psql -U cen -d cen_edu
```

---

## 6. 운영 메모

- **로그**: `docker compose -f docker-compose.prod.yml logs -f backend`. 컨테이너 로그가
  디스크를 채우기 시작하면 compose 에 `logging.options.max-size` 를 건다.
- **DB 접속**: 5432 는 어디에도 열려 있지 않다. 확인은 컨테이너 안에서 한다.
  ```bash
  docker exec -it cen-edu-postgres psql -U cen -d cen_edu
  ```
- **HTTPS**: 도메인을 붙인 다음 단계다. Route 53 에 도메인을 올리고 A 레코드를 EIP 로
  향한 뒤, 프론트 컨테이너의 nginx 에 certbot 을 붙이거나 앞단에 ALB + ACM 인증서를 둔다.
  JWT 를 쓰는 서비스라 **실제 학생 데이터를 넣기 전에는 HTTPS 를 먼저 붙인다** — 지금
  구성은 토큰이 평문으로 오간다.
- **MyScript 키**: `VITE_MYSCRIPT_HMAC_KEY` 는 브라우저 번들에 그대로 실린다(Vite 의
  `VITE_` 값은 전부 공개된다). 개발자 도구를 열면 누구나 꺼내 쓸 수 있으므로, 그 키의
  사용량과 과금을 감수할 수 있는 범위에서만 쓴다. 가리려면 서명을 백엔드에서 만들어
  주는 방식으로 바꿔야 하고, 그건 프론트·백엔드 양쪽 코드 변경이다.
- **Swagger**: 운영에서 닫혀 있다(프론트 nginx 의 `deny` + 백엔드
  `springdoc.*.enabled=false`). 시연 등으로 열어야 하면 `SWAGGER_ENABLED=true` 로 띄우고
  프론트 nginx 의 deny 블록도 함께 푼다(= 프론트 이미지 재빌드).
- **디스크**: `deploy.sh` 가 배포마다 `docker image prune -f` 를 돌린다. `df -h` 로 가끔 확인한다.
- **안 쓸 때는 인스턴스를 중지한다.** 프리티어 750시간은 한 대를 한 달 내내 켜 두면 거의
  다 쓴다. 중지해도 EIP 와 EBS 는 유지되므로 IP 가 바뀌지 않는다(중지 중인 인스턴스에
  붙은 EIP 는 요금이 붙을 수 있으니 청구 알람으로 확인한다).

---

## 6.1 시연 전 점검

배포가 아니라 시연이 목적이므로, 당일 아침에 한 번 훑는다.

```bash
cd ~/app
docker compose -f docker-compose.prod.yml ps          # 4개 모두 Up / healthy
curl -s http://localhost/actuator/health              # {"status":"UP"}
curl -s -o /dev/null -w '%{http_code}
' http://localhost/   # 200
free -h && df -h /                                    # 메모리·디스크 여유
docker exec cen-edu-postgres pg_dump -U cen cen_edu | gzip > ~/backup/demo_$(date +%F).sql.gz
```

- 시연에 쓸 교사·학생 계정으로 **미리 한 번 로그인**해 본다. 첫 요청은 JVM 워밍업 때문에
  느리다 — 시연 중 첫 클릭에서 기다리는 그림이 나오지 않게 한다.
- LLM 을 쓰는 화면(문항 생성, 서술형 채점)은 **미리 한 번 돌려 둔다.** OpenAI 키 만료나
  잔액 부족은 그 자리에서 알 수 있는 게 아니다.
- `docker stats --no-stream` 으로 메모리를 한 번 본다. backend 가 상한(512m)에 계속
  붙어 있으면 그날 시연에서 무거운 화면을 여는 순간 느려진다.

---

## 7. 다음 단계 — 교안의 VPC 분리 구성으로 확장

부하가 늘거나 보안 요구가 생기면 교안의 퍼블릭/프라이빗 분리 구성으로 옮긴다. 이 저장소의
파일은 그대로 쓸 수 있다.

1. 프라이빗 서브넷(`10.0.128.0/24`)과 NAT 게이트웨이를 추가한다(과금 시작).
2. DB 를 프라이빗 서브넷의 EC2(또는 RDS)로 옮기고, `postgres-sg` 는 앱 보안 그룹에서
   오는 5432 만 허용한다.
3. `docker-compose.prod.yml` 에서 `postgres` 서비스와 `DB_URL` 지정을 지우고, `.env` 에
   `DB_URL` 을 사설 IP(또는 RDS 엔드포인트)로 적는다.
4. 앞단에 ALB + ACM 을 두면 HTTPS 종료와 다중 인스턴스 확장이 함께 해결된다.

---

## 8. 시연이 끝나면 — 철거

이 배포는 학습·시연용이다. 끝나고 방치하면 프리티어를 넘기는 순간부터 요금이 나간다.
**순서대로 지운다.**

| 순서 | 대상 | 방법 | 주의 |
|---|---|---|---|
| 1 | 데이터 | `pg_dump` 로 덤프 받아 로컬에 보관 | 지우고 나면 되돌릴 수 없다 |
| 2 | EC2 인스턴스 | EC2 → 인스턴스 선택 → 인스턴스 상태 → **종료(terminate)** | **중지(stop)로는 부족하다** — EBS 볼륨 요금이 계속 나간다 |
| 3 | EBS 볼륨·스냅샷 | EC2 → 볼륨 / 스냅샷에서 잔여분 확인 후 삭제 | 루트 볼륨은 보통 종료 시 함께 삭제된다 |
| 4 | S3 버킷 2개 | 버킷 **비우기** → 삭제 | 객체가 남아 있으면 삭제되지 않는다 |
| 5 | IAM 사용자 `cen-edu-app` | 액세스 키 삭제 → 사용자 삭제 → 정책 삭제 | 키를 먼저 못 쓰게 만드는 것이 핵심 |
| 6 | 키 페어 | EC2 → 키 페어에서 삭제, 로컬 `.ppk` 파일도 삭제 | |
| 7 | SNS 주제·CloudWatch 경보 | 삭제 | 요금은 없지만 알림이 계속 온다 |
| 8 | Docker Hub 저장소 2개 | Docker Hub 에서 삭제 | 두지 않으면 공개 상태로 남는다 |

지운 뒤 **결제 대시보드에서 다음 날 요금이 0 으로 떨어지는지** 한 번 확인한다. 남아 있는
리소스가 있으면 거기서 드러난다.

> 결제 기본 설정의 "CloudWatch 결제 알림 수신" 은 한 번 켜면 끌 수 없다. 요금은 없다.
