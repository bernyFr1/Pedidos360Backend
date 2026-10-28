# syntax=docker/dockerfile:1

# Dockerfile genérico para cualquiera de los módulos Maven del backend.
# Se usa desde infra/apps/docker-compose.yml con:
#   build.args.MODULE = ms-pedidos-360-bff | ms-pedidos-360-orders | ...
#
# El contexto de build es la raíz del repositorio (../.. desde infra/apps).

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
ARG MODULE
COPY ${MODULE}/ ${MODULE}/
WORKDIR /workspace/${MODULE}
RUN mvn -q -DskipTests package && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
