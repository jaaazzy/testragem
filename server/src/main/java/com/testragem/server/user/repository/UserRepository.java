package com.testragem.server.user.repository;

import com.testragem.server.user.entity.User;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {
    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public User create(String email, String displayName, String passwordHash) {
        UUID id = UUID.randomUUID();
        jdbc.sql("INSERT INTO users(id, email, display_name, password_hash) VALUES (:id, :email, :name, :hash)")
                .param("id", id).param("email", email).param("name", displayName)
                .param("hash", passwordHash).update();
        return findById(id).orElseThrow();
    }

    public Optional<User> findById(UUID id) {
        return jdbc.sql("SELECT id, email, display_name, created_at FROM users WHERE id = :id")
                .param("id", id).query(User.class).optional();
    }

    public Optional<Credentials> findCredentials(String email) {
        return jdbc.sql("SELECT id, password_hash, email_verified_at IS NOT NULL AS email_verified FROM users WHERE email = :email")
                .param("email", email).query(Credentials.class).optional();
    }

    // Kept separate from the public user DTO so hashes can never be serialized as users.
    public record Credentials(UUID id, String passwordHash, boolean emailVerified) {}
}
