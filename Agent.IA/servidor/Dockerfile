# Etapa 1: Usar o Maven para compilar o código (Usando a imagem oficial da Eclipse)
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . /app/
RUN mvn clean package -DskipTests

# Etapa 2: Pegar o arquivo compilado e rodar no Java 17
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]