FROM eclipse-temurin:21-jre

RUN apt-get update && apt-get install -y \
  curl \
  dumb-init \
  && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ARG JAVA_OTEL_VERSION=v1.31.0
# RUN curl -L -O https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/latest/download/opentelemetry-javaagent.jar

COPY java-opts.sh /app
RUN chmod +x /app/java-opts.sh

COPY build/libs/omsorgsopptjening-start-innlesning.jar /app/app.jar

ENV TZ="Europe/Oslo"

ENTRYPOINT ["/usr/bin/dumb-init", "--"]

CMD ["bash", "-c", "source java-opts.sh && exec java ${DEFAULT_JVM_OPTS} ${JAVA_OPTS} -jar app.jar $@"]