# ---- Etapa 1: build ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Copiamos primero el wrapper y el pom para aprovechar la cache de capas de Docker
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

# Ahora sí copiamos el código fuente y compilamos el jar
COPY src ./src
RUN ./mvnw clean package -DskipTests -B

# ---- Etapa 2: runtime ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
