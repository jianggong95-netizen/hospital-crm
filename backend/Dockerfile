# ===== 构建阶段 =====
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY crm-common/pom.xml crm-common/pom.xml
COPY crm-system/pom.xml crm-system/pom.xml
COPY crm-boot/pom.xml crm-boot/pom.xml

COPY crm-common/src crm-common/src
COPY crm-system/src crm-system/src
COPY crm-boot/src crm-boot/src
RUN mvn -B -ntp -DskipTests package

# ===== 运行阶段 =====
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S crm && adduser -S crm -G crm
COPY --from=build /workspace/crm-boot/target/crm-api.jar /app/crm-api.jar

USER crm
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health/readiness | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseG1GC", "-jar", "/app/crm-api.jar"]
