FROM openjdk:8-alpine

COPY target/uberjar/whiplash.jar /whiplash/app.jar

EXPOSE 3000

CMD ["java", "-jar", "/whiplash/app.jar"]
