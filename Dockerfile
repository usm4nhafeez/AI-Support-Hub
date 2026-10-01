#FROM ubuntu:latest
#LABEL authors="usm4n"

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/ai-support-hub-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java","-jar","app.jar"]

#ENTRYPOINT ["top", "-b"]