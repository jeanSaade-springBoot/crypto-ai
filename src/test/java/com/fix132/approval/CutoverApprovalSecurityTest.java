package com.fix132.approval;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import javax.sql.DataSource;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CutoverApprovalSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({CutoverApprovalSecurity.class,CutoverApprovalController.class})
    static class Config {
        @Bean DataSource dataSource(){
            var ds=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE app_user(username VARCHAR(160),password VARCHAR(100),enabled BOOLEAN,role_name VARCHAR(50))");
            jdbc.update("INSERT INTO app_user VALUES('approver','{noop}test',TRUE,'CUTOVER_APPROVER'),('ordinary','{noop}test',TRUE,'USER')");
            return ds;
        }
        @Bean CutoverApprovalService service(){return mock(CutoverApprovalService.class);}
    }
    AnnotationConfigWebApplicationContext context;MockMvc mvc;
    @BeforeEach void setup(){
        context=new AnnotationConfigWebApplicationContext();context.setServletContext(new MockServletContext());
        context.register(Config.class);context.refresh();
        mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean("springSecurityFilterChain",jakarta.servlet.Filter.class)).build();
    }
    @AfterEach void close(){context.close();}
    String basic(String user){return "Basic "+Base64.getEncoder().encodeToString((user+":test").getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    @Test void authenticationPermissionAndCsrfAreIndependentGates() throws Exception {
        mvc.perform(get("/api/fix132/cutover/csrf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fix132/cutover/csrf").header("Authorization",basic("ordinary"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/fix132/cutover/preview").header("Authorization",basic("approver"))
            .contentType("application/json").content("{\"symbol\":\"BTCUSDT\",\"operation\":\"FIRST_CUTOVER\"}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(context.getBean(CutoverApprovalService.class));
        var token=mvc.perform(get("/api/fix132/cutover/csrf").header("Authorization",basic("approver")))
            .andExpect(status().isOk()).andReturn();
        var json=new com.fasterxml.jackson.databind.ObjectMapper().readTree(token.getResponse().getContentAsString());
        mvc.perform(post("/api/fix132/cutover/preview").header("Authorization",basic("approver"))
            .session((MockHttpSession)token.getRequest().getSession(false))
            .header(json.get("headerName").asText(),json.get("token").asText())
            .contentType("application/json").content("{\"symbol\":\"BTCUSDT\",\"operation\":\"FIRST_CUTOVER\"}"))
            .andExpect(status().isOk());
        verify(context.getBean(CutoverApprovalService.class)).preview(eq("BTCUSDT"),eq("FIRST_CUTOVER"),argThat(a->a.getName().equals("approver")));
        mvc.perform(post("/api/fix132/cutover/preview").header("Authorization",basic("ordinary"))
            .session((MockHttpSession)token.getRequest().getSession(false))
            .header(json.get("headerName").asText(),json.get("token").asText())
            .contentType("application/json").content("{\"symbol\":\"BTCUSDT\",\"operation\":\"FIRST_CUTOVER\"}"))
            .andExpect(status().isForbidden());
        verifyNoMoreInteractions(context.getBean(CutoverApprovalService.class));
        mvc.perform(get("/api/wallet").header("Authorization",basic("approver"))).andExpect(status().isForbidden());
    }
}
