FROM openjdk:14-alpine

COPY target/uberjar/whiplash.jar /whiplash/app.jar
RUN wget -O dd-java-agent.jar 'https://github.com/DataDog/dd-trace-java/releases/download/v0.58.0/dd-java-agent-0.58.0.jar'
EXPOSE 3000

CMD ["java", \
"-javaagent:./dd-java-agent.jar", \
"-Ddd.profiling.enabled=true", \
"-Ddd.logs.injection=true", \
"-Ddd.trace.analytics.enabled=true", \
"-jar", \
"/whiplash/app.jar"]
#-Ddd.service=whiplash-web
#-Ddd.env=prod
