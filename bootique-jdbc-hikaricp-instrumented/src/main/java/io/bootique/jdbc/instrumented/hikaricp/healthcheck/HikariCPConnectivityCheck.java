package io.bootique.jdbc.instrumented.hikaricp.healthcheck;

import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.pool.HikariPool;
import io.bootique.metrics.health.HealthCheck;
import io.bootique.metrics.health.HealthCheckOutcome;
import io.bootique.metrics.health.check.Threshold;
import io.bootique.metrics.health.check.ThresholdType;
import io.bootique.metrics.health.check.ValueRange;
import io.bootique.value.Duration;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

/**
 * HikariCP standard "aliveness" check.
 */
public class HikariCPConnectivityCheck implements HealthCheck {

    private final HikariPoolMXBean pool;
    private final ValueRange<Duration> timeoutThresholds;

    public HikariCPConnectivityCheck(HikariPoolMXBean pool, ValueRange<Duration> timeoutThresholds) {
        this.pool = pool;
        this.timeoutThresholds = timeoutThresholds;
    }

    /**
     * Generates a stable qualified name for the {@link HikariCPConnectivityCheck}
     *
     * @param dataSourceName Bootique configuration name of the data source being checked.
     * @return qualified name bq.jdbc.[dataSourceName].connectivity
     */
    public static String healthCheckName(String dataSourceName) {
        return "bq.jdbc." + dataSourceName + ".connectivity";
    }

    /**
     * The health check obtains a {@link Connection} from the pool and immediately return it.
     * The standard HikariCP internal "aliveness" check will be run.
     *
     * @return {@link HealthCheckOutcome#ok()} if an "alive" {@link Connection} can be obtained,
     * otherwise {@link HealthCheckOutcome#critical()} if the connection fails or times out
     */
    @Override
    public HealthCheckOutcome check() {

        HealthCheckOutcome warningOutcome = checkThreshold(ThresholdType.WARNING, HealthCheckOutcome::warning);

        switch (warningOutcome.getStatus()) {
            case WARNING:
                return warningOutcome;
            case UNKNOWN:
                return checkThreshold(ThresholdType.CRITICAL, HealthCheckOutcome::critical);
        }

        return HealthCheckOutcome.ok();
    }

    protected HealthCheckOutcome checkThreshold(ThresholdType type, Function<SQLException, HealthCheckOutcome> onFailure) {

        Threshold<Duration> threshold = timeoutThresholds.getThreshold(type);
        if (threshold == null) {
            return HealthCheckOutcome.unknown();
        }

        long timeout = threshold.getValue().getDuration().toMillis();

        try (Connection connection = ((HikariPool) pool).getConnection(timeout)) {
            return HealthCheckOutcome.ok();
        } catch (SQLException e) {
            return onFailure.apply(e);
        }
    }
}