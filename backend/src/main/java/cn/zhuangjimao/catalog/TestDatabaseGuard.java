package cn.zhuangjimao.catalog;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

/** Runs after config loading but before datasource creation and Flyway. */
public class TestDatabaseGuard implements EnvironmentPostProcessor, Ordered {
    private static final Set<String> SAFE_JDBC_OPTIONS = Set.of("characterEncoding", "connectionTimeZone",
            "connectTimeout", "socketTimeout", "sslMode", "allowPublicKeyRetrieval");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!Arrays.asList(environment.getActiveProfiles()).contains("test")) return;
        String url = require(environment, "TEST_DB_URL");
        String user = require(environment, "TEST_DB_USERNAME");
        String password = require(environment, "TEST_DB_PASSWORD");
        var binder = Binder.get(environment);
        for (String property : Set.of("spring.flyway.url", "spring.flyway.user", "spring.flyway.password",
                "spring.flyway.schemas", "spring.flyway.default-schema", "spring.flyway.init-sqls",
                "spring.datasource.jndi-name", "spring.datasource.type",
                "spring.datasource.hikari.jdbc-url", "spring.datasource.hikari.username",
                "spring.datasource.hikari.password", "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.data-source-jndi", "spring.datasource.hikari.driver-class-name",
                "spring.datasource.hikari.schema", "spring.datasource.hikari.catalog",
                "spring.datasource.hikari.connection-init-sql", "spring.datasource.hikari.connection-test-query")) {
            if (binder.bind(property, String.class).isBound()) throw refuse("connection override is forbidden: " + property);
        }
        if (binder.bind("spring.datasource.hikari.data-source-properties",
                Bindable.mapOf(String.class, String.class)).isBound()) {
            throw refuse("Hikari datasource properties cannot override test connection identity");
        }
        URI database;
        try { database = URI.create(url.substring("jdbc:".length())); }
        catch (RuntimeException exception) { throw refuse("invalid test MySQL URL"); }
        if (!url.startsWith("jdbc:mysql://") || database.getHost() == null
                || !"/pc_mall_test".equals(database.getRawPath()) || database.getUserInfo() != null
                || !"pc_mall_test".equals(user)
                || !url.equals(environment.getProperty("spring.datasource.url"))
                || !user.equals(environment.getProperty("spring.datasource.username"))
                || !password.equals(environment.getProperty("spring.datasource.password"))) {
            throw refuse("only explicit pc_mall_test schema and account are allowed; datasource overrides are forbidden");
        }
        if (database.getRawQuery() != null) {
            for (String option : database.getRawQuery().split("&", -1)) {
                int equals = option.indexOf('=');
                if (equals < 1 || !SAFE_JDBC_OPTIONS.contains(option.substring(0, equals))) {
                    throw refuse("JDBC options must not override database or account identity");
                }
            }
        }
    }

    private static String require(ConfigurableEnvironment environment, String name) {
        String value = environment.getProperty(name);
        if (value == null || value.isBlank()) throw refuse(name + " is required");
        return value;
    }

    private static IllegalStateException refuse(String message) {
        return new IllegalStateException("Test database guard: " + message);
    }

    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
