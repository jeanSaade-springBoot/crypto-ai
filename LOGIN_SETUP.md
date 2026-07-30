# Database Login

The dashboard and every `/api/**` endpoint require a database-backed login.
No user-management module or JPA user entity is used. Spring Security reads
users directly from the `app_user` table created by Flyway migration
`V7__create_app_user_table.sql`.

## First login

- Username: `admin`
- Password: `ChangeMe123!`

Change the initial password immediately.

## Add a user

Password values use Spring Security's delegated format. For local testing only:

```sql
INSERT INTO app_user (username, password, role_name, enabled)
VALUES ('jean', '{noop}temporary-password', 'ADMIN', TRUE);
```

For a real environment, store a BCrypt value:

```sql
INSERT INTO app_user (username, password, role_name, enabled)
VALUES ('jean', '{bcrypt}$2a$10$...', 'ADMIN', TRUE);
```

Do not store an unprefixed plain-text password. Disable a user without deleting
its history:

```sql
UPDATE app_user SET enabled = FALSE WHERE username = 'jean';
```

The login page is `/login.html`; successful login redirects to `/dashboard`.
The dashboard Logout button invalidates the session and removes `JSESSIONID`.
