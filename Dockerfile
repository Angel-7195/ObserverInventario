FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY *.java index.html ./
RUN javac -encoding UTF-8 *.java
CMD ["java", "-Djava.awt.headless=true", "ServidorWeb"]
