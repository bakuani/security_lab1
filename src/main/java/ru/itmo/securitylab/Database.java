package ru.itmo.securitylab;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class Database {
    private final JdbcTemplate jdbc;

    public Database(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record User(long id, String username, String passwordHash) { }
    public record Task(long id, String title, String description, String status) { }

    public Optional<User> findUser(String username) {
        return jdbc.query("SELECT id, username, password_hash FROM app_user WHERE username = ?",
                (rs, row) -> new User(rs.getLong("id"), rs.getString("username"),
                        rs.getString("password_hash")), username).stream().findFirst();
    }

    public List<Task> tasks(long ownerId) {
        return jdbc.query("SELECT id, title, description, status FROM task WHERE owner_id = ? ORDER BY id",
                (rs, row) -> new Task(rs.getLong("id"), rs.getString("title"),
                        rs.getString("description"), rs.getString("status")), ownerId);
    }

    public boolean changeStatus(long id, long ownerId, String status) {
        return jdbc.update("UPDATE task SET status = ? WHERE id = ? AND owner_id = ?",
                status, id, ownerId) == 1;
    }

    public void addUser(String username, String passwordHash) {
        jdbc.update("INSERT INTO app_user(username, password_hash) VALUES (?, ?)", username, passwordHash);
    }

    public void addTask(long ownerId, String title, String description) {
        jdbc.update("INSERT INTO task(owner_id, title, description, status) VALUES (?, ?, ?, ?)",
                ownerId, title, description, "OPEN");
    }
}
