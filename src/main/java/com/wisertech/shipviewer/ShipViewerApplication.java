// main: starts the Spring Boot ship viewer application with its viewer properties enabled.

package com.wisertech.shipviewer;

import com.wisertech.shipviewer.config.ViewerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ViewerProperties.class)
public class ShipViewerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShipViewerApplication.class, args);
    }
}
