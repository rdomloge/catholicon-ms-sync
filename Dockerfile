FROM openjdk:11.0-jdk-slim

RUN apt update && apt install -y procps && apt install net-tools

EXPOSE 8080
EXPOSE 8000

ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:8000", "-jar", "catholiconmssync.jar"]

COPY target/catholiconmssync.jar catholiconmssync.jar