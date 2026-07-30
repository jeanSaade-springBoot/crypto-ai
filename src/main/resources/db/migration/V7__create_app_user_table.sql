CREATE TABLE app_user (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role_name VARCHAR(50) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_app_user_username UNIQUE (username)
);

-- Initial local administrator.
-- Username: admin
-- Password: ChangeMe123!
-- Change or delete this row immediately after the first login.
-- {noop} is included only to make the first local login possible. New passwords
-- should be stored as {bcrypt}<bcrypt-hash>.
INSERT INTO app_user (username, password, role_name, enabled)
VALUES ('admin', '{noop}ChangeMe123!', 'ADMIN', TRUE);
