package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.Users.USERS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UserPasswordCredentialRepositoryJooqImplTest {

    @Test
    void upsertAndReadHashAgainstFreshDemoSchema() throws Exception {
        String password = System.getenv("DB_PASSWORD");
        assumeTrue(password != null && !password.isBlank(), "DB_PASSWORD is needed for database integration test");
        String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://localhost:5432/backend_db?currentSchema=backend_data");

        try (Connection connection = DriverManager.getConnection(url, "postgres", password)) {
            connection.setAutoCommit(false);
            try {
                DSLContext dsl = DSL.using(connection, SQLDialect.POSTGRES);
                Long userId = dsl.insertInto(USERS)
                        .set(USERS.EMAIL, "password-test-" + UUID.randomUUID() + "@example.test")
                        .set(USERS.EMAIL_VERIFIED, true)
                        .returning(USERS.ID)
                        .fetchOne()
                        .getId();
                UserPasswordCredentialRepositoryJooqImpl repository =
                        new UserPasswordCredentialRepositoryJooqImpl(dsl);

                repository.upsertPasswordHash(userId, "first-hash");
                assertEquals("first-hash", repository.findPasswordHash(userId).orElseThrow());
                repository.upsertPasswordHash(userId, "replacement-hash");
                assertEquals("replacement-hash", repository.findPasswordHash(userId).orElseThrow());
            } finally {
                connection.rollback();
            }
        }
    }
}
