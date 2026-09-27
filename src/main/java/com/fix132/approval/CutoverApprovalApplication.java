package com.fix132.approval;

import com.crypto.shared.SharedMarketSource;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import javax.sql.DataSource;

/** Deliberately outside com.crypto's component scan. Launch with the explicit
 * --fix132-approval-console=true argument after stopping ALL Trader instances.
 * No @EnableScheduling / application component scan / business services. */
@Configuration(proxyBeanMethods=false)
@EnableAutoConfiguration(excludeName={"org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
    "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"})
@Import({SharedMarketSource.class,CutoverApprovalService.class,CutoverApprovalController.class,CutoverApprovalSecurity.class})
public class CutoverApprovalApplication {
    @Bean Object requireObservationMode(Environment env) {
        if(!"OBSERVE".equals(env.getProperty("shared-market.mode","OFF")))
            throw new IllegalStateException("FIX-132 approval console requires OBSERVE; never LIVE");
        return new Object();
    }
}
