# Etapa 1: Usar o Maven para compilar o código
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . /app/
RUN mvn clean package -DskipTests

# Etapa 2: Pegar o arquivo compilado e rodar no Java 17
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]