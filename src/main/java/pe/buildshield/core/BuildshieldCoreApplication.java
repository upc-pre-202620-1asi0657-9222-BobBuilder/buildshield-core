package pe.buildshield.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BuildshieldCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuildshieldCoreApplication.class, args);
    }
}
