// addResourceHandlers: serves raster tiles from the tiles folder, cached privately as immutable for a year.

package com.wisertech.shipviewer.config;

import com.wisertech.shipviewer.web.dto.RasterLayer;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RasterResources implements WebMvcConfigurer {
    private static final Duration FOREVER = Duration.ofDays(365);

    private final ViewerProperties properties;

    public RasterResources(ViewerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if (properties.tilesDir() != null) {
            registry.addResourceHandler(RasterLayer.TILES_PREFIX + "**")
                    .addResourceLocations(properties.tilesDir().toUri().toString())
                    .setCacheControl(CacheControl.maxAge(FOREVER).cachePrivate().immutable());
        }
    }
}
