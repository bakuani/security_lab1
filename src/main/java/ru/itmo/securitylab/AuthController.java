package ru.itmo.securitylab;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AuthController {
    private final Database database;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;

    public AuthController(Database database, PasswordEncoder encoder, JwtEncoder jwtEncoder) {
        this.database = database;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
    }

    public record Login(@NotBlank @Size(max = 64) String username,
                        @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "Login[redacted]"; }
    }
    public record Token(String accessToken, String tokenType, long expiresIn) {
        @Override public String toString() { return "Token[redacted]"; }
    }

    @PostMapping("/auth/login")
    public Token login(@Valid @RequestBody Login input) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var user = database.findUser(input.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!encoder.matches(input.password(), user.passwordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(SecurityConfig.ISSUER).audience(List.of(SecurityConfig.AUDIENCE))
                .subject(Long.toString(user.id()))
                .issuedAt(now).expiresAt(now.plusSeconds(900)).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Token(token, "Bearer", 900);
    }
}
