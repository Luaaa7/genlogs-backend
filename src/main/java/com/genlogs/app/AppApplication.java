package com.genlogs.app;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AppApplication {

    public static void main(String[] args) {
        // Render corre en UTC: sin esto, después de las 7 p. m. de Perú las fechas
        // (código de cotización, emisión, vencimientos, reportes) salían con el día siguiente.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(AppApplication.class, args);
    }
}