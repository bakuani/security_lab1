package ru.itmo.securitylab;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration(proxyBeanMethods = false)
public class DemoData {
    @Bean
    CommandLineRunner seed(Database database, PasswordEncoder encoder,
                           @Value("${lab.alice-password}") String alicePassword,
                           @Value("${lab.bob-password}") String bobPassword) {
        validatePassword(alicePassword);
        validatePassword(bobPassword);
        // Only hashes are captured by the runner; passwords are never inserted in SQL.
        String aliceHash = encoder.encode(alicePassword);
        String bobHash = encoder.encode(bobPassword);
        return args -> {
            database.addUser("alice", aliceHash);
            database.addUser("bob", bobHash);
            long aliceId = database.findUser("alice").orElseThrow().id();
            long bobId = database.findUser("bob").orElseThrow().id();
            database.addTask(aliceId, "Изучить JWT", "Проверить подпись и срок действия токена");
            database.addTask(aliceId, "Проверить XSS: <script>alert('demo')</script>",
                    "Строка <img src=x onerror=alert(1)> должна возвращаться как текст");
            database.addTask(bobId, "Задача Bob", "Эта задача доступна только владельцу");
        };
    }

    private static void validatePassword(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 12 || bytes > 72) {
            throw new IllegalArgumentException("Demo passwords must contain at least 12 characters and at most 72 UTF-8 bytes");
        }
    }
}
