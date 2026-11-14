package com.crypto.shared;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** FIX-147: migration account semantics on H2, not a substitute for MySQL Flyway validation. */
class CutoverApproverMigrationTest {
    private JdbcTemplate jdbc;
    @BeforeEach void setup() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
        jdbc.execute("CREATE TABLE app_user(id BIGINT AUTO_INCREMENT PRIMARY KEY,username VARCHAR(100) UNIQUE NOT NULL,password VARCHAR(255) NOT NULL,role_name VARCHAR(50) NOT NULL,enabled BOOLEAN NOT NULL)");
    }
    void migrate() throws Exception {
        jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V102__fix_147_cutover_approver_account.sql")));
    }
    @Test void createsDedicatedApproverWithoutChangingAdminAndDoesNotDuplicateOnRepeat() throws Exception {
        jdbc.update("INSERT INTO app_user(username,password,role_name,enabled) VALUES('admin',?,'ADMIN',TRUE)","{bcrypt}fixture-encoded-value");
        migrate();migrate();
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class));
        assertEquals("ADMIN",jdbc.queryForObject("SELECT role_name FROM app_user WHERE username='admin'",String.class));
        assertEquals("CUTOVER_APPROVER",jdbc.queryForObject("SELECT role_name FROM app_user WHERE username='cutover_approver'",String.class));
        assertEquals(jdbc.queryForObject("SELECT password FROM app_user WHERE username='admin'",String.class),jdbc.queryForObject("SELECT password FROM app_user WHERE username='cutover_approver'",String.class));
        assertTrue(jdbc.queryForObject("SELECT enabled FROM app_user WHERE username='cutover_approver'",Boolean.class));
    }
    @Test void preservesExistingApproverIncludingDisabledStateAndCredentials() throws Exception {
        jdbc.update("INSERT INTO app_user(username,password,role_name,enabled) VALUES('admin','admin-value','ADMIN',TRUE),('cutover_approver','existing-value','CUTOVER_APPROVER',FALSE)");
        migrate();
        assertEquals("existing-value",jdbc.queryForObject("SELECT password FROM app_user WHERE username='cutover_approver'",String.class));
        assertFalse(jdbc.queryForObject("SELECT enabled FROM app_user WHERE username='cutover_approver'",Boolean.class));
    }
    @Test void absentOrDisabledAdministratorCannotProvisionApprovalAccess() throws Exception {
        migrate();assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class));
        jdbc.update("INSERT INTO app_user(username,password,role_name,enabled) VALUES('admin','fixture','ADMIN',FALSE)");
        migrate();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class));
    }
    @Test void wrongAdministratorRoleOrBlankCredentialDoesNotGrantApprovalAccess() throws Exception {
        jdbc.update("INSERT INTO app_user(username,password,role_name,enabled) VALUES('admin','fixture','USER',TRUE)");
        migrate();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class));
        jdbc.update("UPDATE app_user SET role_name='ADMIN',password='   '");
        migrate();assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM app_user",Integer.class));
    }
}
