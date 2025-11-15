# ================================
# 1) Etapa de build (Maven)
# ================================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copiamos pom y código
COPY pom.xml .
COPY src ./src

# Construimos el JAR ejecutable de Spring Boot
# (limpia, empaqueta y re-empaqueta como fat jar)
RUN mvn -q -DskipTests clean package spring-boot:repackage

# ================================
# 2) Etapa de runtime (solo JRE)
# ================================
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Copiamos el JAR desde la etapa de build
COPY --from=builder /app/target/microservice-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
