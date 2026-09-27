package com.fix132.approval;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import javax.sql.DataSource;

@Configuration(proxyBeanMethods=false)
public class CutoverApprovalSecurity {
    @Bean SecurityFilterChain approvalSecurity(HttpSecurity http) throws Exception {
        // CSRF remains ENABLED. Authentication and permission are separate gates.
        return http.authorizeHttpRequests(a->a.requestMatchers("/api/fix132/cutover/**").hasRole("CUTOVER_APPROVER")
            .anyRequest().denyAll()).httpBasic(org.springframework.security.config.Customizer.withDefaults())
            .build();
    }
    @Bean JdbcUserDetailsManager approvalUsers(DataSource dataSource) {
        var users=new JdbcUserDetailsManager(dataSource);
        users.setUsersByUsernameQuery("SELECT username,password,enabled FROM app_user WHERE username=?");
        users.setAuthoritiesByUsernameQuery("SELECT username,CONCAT('ROLE_',role_name) FROM app_user WHERE username=? AND enabled=TRUE");
        return users;
    }
}
