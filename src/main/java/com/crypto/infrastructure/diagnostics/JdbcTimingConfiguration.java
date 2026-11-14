package com.crypto.infrastructure.diagnostics;

import javax.sql.DataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** FIX-142: decorate the existing datasource, preserving Boot/Flyway/JPA ownership.
 * Every SQL operation is logged when enabled; no sampling hides individual calls. */
@Configuration
public class JdbcTimingConfiguration {
    @Bean public static BeanPostProcessor jdbcTimingPostProcessor(Environment env) {
        return new BeanPostProcessor() {
            public Object postProcessAfterInitialization(Object bean,String name) {
                if(bean instanceof DataSource ds && !(bean instanceof JdbcTimingDataSource)
                    && env.getProperty("diagnostics.jdbc.enabled",Boolean.class,true))
                    return new JdbcTimingDataSource(ds,name,env.getProperty("diagnostics.jdbc.slow-ms",Long.class,250L));
                return bean;
            }
        };
    }
}
