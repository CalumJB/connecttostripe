FROM openjdk:17
WORKDIR /app
COPY target/connecttostripe-1.24.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]