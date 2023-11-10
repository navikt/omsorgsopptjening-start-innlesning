FROM ghcr.io/navikt/baseimages/temurin:17

COPY build/libs/omsorgsopptjening-start-innlesning.jar /app/app.jar
