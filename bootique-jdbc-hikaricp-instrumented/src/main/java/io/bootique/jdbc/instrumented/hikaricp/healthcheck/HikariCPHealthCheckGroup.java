package io.bootique.jdbc.instrumented.hikaricp.healthcheck;

import io.bootique.jdbc.instrumented.hikaricp.managed.InstrumentedManagedDataSourceStarter;
import io.bootique.jdbc.managed.ManagedDataSourceStarter;
import io.bootique.metrics.health.HealthCheck;
import io.bootique.metrics.health.HealthCheckGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * A container of HikariCP-specific health checks.
 */
public class HikariCPHealthCheckGroup implements HealthCheckGroup {

    private Map<String, ManagedDataSourceStarter> starters;

    public HikariCPHealthCheckGroup(Map<String, ManagedDataSourceStarter> starters) {
        this.starters = starters;
    }

    @Override
    public Map<String, HealthCheck> getHealthChecks() {

        Map<String, HealthCheck> checks = new HashMap<>();

        starters.forEach((b, s) -> {

            // extract health checks from Hikari instrumented starters
            if (s instanceof InstrumentedManagedDataSourceStarter) {
                checks.putAll(((InstrumentedManagedDataSourceStarter) s).getHealthChecks().getHealthChecks());
            }
        });

        return checks;
    }
}
