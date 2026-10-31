package com.xiaoyuan.paperpuppybackendintl.repository.jooq;

import com.xiaoyuan.paperpuppybackendintl.repository.UserModelCredentialRepository.StoredCredential;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static com.xiaoyuan.paperpuppybackendintl.infrastructure.jooq.generated.tables.Users.USERS;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UserModelCredentialRepositoryJooqImplTest {

    @Test
    void storesCiphertextPerUserAndProviderAgainstMigratedDemoSchema() throws Exception {
        String password = System.getenv("DB_PASSWORD");
        assumeTrue(password != null && !password.isBlank(), "DB_PASSWORD is needed for database integration test");
        String url = System.getenv().getOrDefault("SPRING_DATASOURCE_URL",
                "jdbc:postgresql://localhost:5432/backend_db?currentSchema=backend_data");

        try (Connection connection = DriverManager.getConnection(url, "postgres", password)) {
            connection.setAutoCommit(false);
            try {
                DSLContext dsl = DSL.using(connection, SQLDialect.POSTGRES);
                Long firstUser = createUser(dsl);
                Long secondUser = createUser(dsl);
                var repository = new UserModelCredentialRepositoryJooqImpl(dsl);
                byte[] firstCiphertext = new byte[]{1, 2, 3, 4};
                byte[] secondCiphertext = new byte[]{5, 6, 7, 8};
                byte[] nonce = new byte[12];

                repository.upsert(new StoredCredential(firstUser, "deepseek", firstCiphertext, nonce, 1, "1234"));
                repository.upsert(new StoredCredential(secondUser, "deepseek", secondCiphertext, nonce, 1, "5678"));
                assertArrayEquals(firstCiphertext, repository.find(firstUser, "deepseek").orElseThrow().ciphertext());
                assertArrayEquals(secondCiphertext, repository.find(secondUser, "deepseek").orElseThrow().ciphertext());
                assertTrue(repository.find(firstUser, "openai").isEmpty());
                assertEquals(1, repository.list(firstUser).size());

                byte[] updated = new byte[]{9, 10};
                repository.upsert(new StoredCredential(firstUser, "deepseek", updated, nonce, 1, "9010"));
                assertArrayEquals(updated, repository.find(firstUser, "deepseek").orElseThrow().ciphertext());
                assertTrue(repository.delete(secondUser, "deepseek"));
                assertTrue(repository.find(secondUser, "deepseek").isEmpty());
                assertTrue(repository.find(firstUser, "deepseek").isPresent());
            } finally {
                connection.rollback();
            }
        }
    }

    private Long createUser(DSLContext dsl) {
        return dsl.insertInto(USERS)
                .set(USERS.EMAIL, "byok-test-" + UUID.randomUUID() + "@example.test")
                .set(USERS.EMAIL_VERIFIED, true)
                .returning(USERS.ID)
                .fetchOne()
                .getId();
    }
}
