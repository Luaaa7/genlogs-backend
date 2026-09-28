# ---- Etapa 1: build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Copiamos primero solo lo necesario para resolver dependencias (mejor cache de Docker)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# Ahora copiamos el código y compilamos
COPY src ./src
RUN ./mvnw -B clean package -DskipTests

# ---- Etapa 2: runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Render inyecta la variable PORT; el application.properties ya la lee con ${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]