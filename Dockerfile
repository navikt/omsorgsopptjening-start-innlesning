FROM ghcr.io/navikt/baseimages/temurin:21

COPY build/libs/omsorgsopptjening-start-innlesning.jar /app/app.jar

ENV JAVA_OPTS=-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/
