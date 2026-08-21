# CEN EDU 백엔드 배포 이미지. 로컬(또는 CI)에서 빌드해 Docker Hub 로 push 하고,
# EC2 는 pull 만 한다 — t3.micro(1GB) 에서 Gradle 빌드를 돌리면 OOM 으로 죽는다.
#
#   docker build -t $DOCKERHUB_USER/cen-edu-backend:1.0.0 .
#   docker push  $DOCKERHUB_USER/cen-edu-backend:1.0.0
#
# 태그에 latest 를 쓰지 않는다. EC2 에서 어떤 코드가 도는지 구분되지 않고,
# 롤백할 이전 태그도 남지 않는다.

# ---------- 1단계: 빌드 ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# 래퍼와 의존성 선언을 먼저 복사해 Gradle 배포판·의존성 내려받기를 캐시 레이어로 굳힌다.
# 소스만 바뀐 재빌드에서 이 레이어가 그대로 재사용된다.
COPY gradlew ./
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
# 테스트는 CI(PR)에서 이미 돌았다. 이미지 빌드에서 다시 돌리면 DB 가 필요한 테스트가 깨진다.
RUN ./gradlew --no-daemon clean bootJar -x test

# ---------- 2단계: 실행 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# 로그·PDF 렌더링 결과의 타임스탬프가 한국 시간으로 찍혀야 학생 회차와 대조가 된다.
ENV TZ=Asia/Seoul
# 컨테이너 메모리 상한의 75% 를 힙으로 잡는다. -Xmx 를 숫자로 박으면 인스턴스를
# 키워도 힙이 따라 커지지 않는다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# compose 헬스체크가 /actuator/health 를 때린다. JRE 이미지에는 curl 이 없어 넣어 준다.
RUN apt-get update  && apt-get install -y --no-install-recommends curl  && rm -rf /var/lib/apt/lists/*

# root 로 돌리지 않는다. 컨테이너가 뚫려도 호스트로 넘어갈 여지를 줄인다.
RUN useradd --system --create-home --shell /usr/sbin/nologin cenedu
COPY --from=build /workspace/build/libs/*.jar app.jar
RUN chown cenedu:cenedu /app/app.jar
USER cenedu

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
