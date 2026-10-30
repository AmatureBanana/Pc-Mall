package cn.zhuangjimao.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDatabaseSafetyTest {
    @ParameterizedTest
    @ValueSource(strings = {"spring.flyway.url", "spring.datasource.hikari.jdbc-url"})
    void refusesIndependentConnectionOverrides(String property) {
        var application = new SpringApplication(MallApplication.class);
        application.setAdditionalProfiles("test");
        assertThatThrownBy(() -> application.run("--server.port=0",
                "--" + property + "=jdbc:mysql://127.0.0.1:1/pc_mall_demo"))
                .hasStackTraceContaining("Test database guard:");
    }

    @Test
    void refusesDemoDatabaseBeforeAnyConnectionOrMigration() {
        assertThatThrownBy(() -> ProductBrowserTest.start(
                "jdbc:mysql://127.0.0.1:1/pc_mall_demo", "pc_mall_demo", "unused"))
                .hasStackTraceContaining("Test database guard:");
    }

    @Test
    void refusesMissingExplicitTestConfiguration() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        var application = new SpringApplication(MallApplication.class);
        application.setEnvironment(environment);
        application.setAdditionalProfiles("test");
        assertThatThrownBy(() -> application.run("--server.port=0"))
                .hasStackTraceContaining("Test database guard: TEST_DB_URL is required");
    }
}
