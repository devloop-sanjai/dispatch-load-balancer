package com.freightfox.dispatch.config;

import com.freightfox.dispatch.optimizer.DispatchOptimizer;
import com.freightfox.dispatch.optimizer.DistanceCalculator;
import com.freightfox.dispatch.optimizer.GreedyDispatchOptimizer;
import com.freightfox.dispatch.optimizer.HaversineDistanceCalculator;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    public DistanceCalculator distanceCalculator() {
        return new HaversineDistanceCalculator();
    }

    @Bean
    public DispatchOptimizer dispatchOptimizer(DistanceCalculator distanceCalculator) {
        return new GreedyDispatchOptimizer(distanceCalculator);
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI().info(new Info()
                .title("Dispatch Load Balancer API")
                .version("1.0.0")
                .description("Assigns delivery orders to vehicles respecting capacity and priority, "
                        + "minimizing travel distance (Haversine)."));
    }
}
