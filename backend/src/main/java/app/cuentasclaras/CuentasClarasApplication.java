package app.cuentasclaras;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CuentasClarasApplication {

    public static void main(String[] args) {
        SpringApplication.run(CuentasClarasApplication.class, args);
    }
}
