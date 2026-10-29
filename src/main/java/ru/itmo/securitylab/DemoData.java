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
                           @Value("${lab.anna-password}") String annaPassword,
                           @Value("${lab.ivan-password}") String ivanPassword) {
        validatePassword(annaPassword);
        validatePassword(ivanPassword);
        // В базу записываются только хэши паролей.
        String annaHash = encoder.encode(annaPassword);
        String ivanHash = encoder.encode(ivanPassword);
        return args -> {
            database.addUser("anna", annaHash);
            database.addUser("ivan", ivanHash);
            long annaId = database.findUser("anna").orElseThrow().id();
            long ivanId = database.findUser("ivan").orElseThrow().id();
            database.addTask(annaId, "Изучить JWT", "Проверить подпись и срок действия токена");
            database.addTask(annaId, "Проверить XSS: <script>alert('demo')</script>",
                    "Строка <img src=x onerror=alert(1)> должна возвращаться как текст");
            database.addTask(ivanId, "Задача Ivan", "Эта задача доступна только владельцу");
        };
    }

    private static void validatePassword(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 12 || bytes > 72) {
            throw new IllegalArgumentException("Demo passwords must contain at least 12 characters and at most 72 UTF-8 bytes");
        }
    }
}
