package ru.itmo.securitylab;

import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.servlet.http.HttpServletResponse;

public record ApiError(String error) {
    private static final ObjectWriter JSON = new ObjectMapper().writerFor(ApiError.class);
    public static void write(HttpServletResponse response, int status, String error) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        // Serialize as JSON; all call sites pass fixed server-side messages.
        JSON.writeValue(response.getWriter(), new ApiError(error));
    }
}
