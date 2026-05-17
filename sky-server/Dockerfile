FROM eclipse-temurin:8-jdk-alpine AS build

WORKDIR /workspace

# The workflow builds the module first, so this stage only stages the packaged jar.
COPY target/*-SNAPSHOT.jar app.jar

FROM eclipse-temurin:8-jre

WORKDIR /app

# Keep the heap capped so two JVMs can coexist on the 2 GB host.
ENV JAVA_OPTS="-Xms256m -Xmx512m -Dhttps.protocols=TLSv1.2,TLSv1.3 -Djdk.tls.client.protocols=TLSv1.2,TLSv1.3"


COPY --from=build /workspace/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
