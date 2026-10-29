package ru.itmo.securitylab;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;

@RestController
@Validated
public class TaskController {
    private final Database database;

    public TaskController(Database database) {
        this.database = database;
    }

    public enum Status { OPEN, IN_PROGRESS, DONE }
    public record StatusChange(@NotNull Status status) { }
    public record TaskView(long id, String title, String description, String status) {
        static TaskView from(Database.Task task) {
            return new TaskView(task.id(), HtmlUtils.htmlEscape(task.title()),
                    HtmlUtils.htmlEscape(task.description()), HtmlUtils.htmlEscape(task.status()));
        }
    }

    @GetMapping("/api/data")
    public List<TaskView> list(@AuthenticationPrincipal Jwt jwt) {
        return database.tasks(Long.parseLong(jwt.getSubject())).stream().map(TaskView::from).toList();
    }

    @PatchMapping("/api/data/{id}/status")
    public StatusChange changeStatus(@PathVariable @Positive long id,
                                      @Valid @RequestBody StatusChange input,
                                      @AuthenticationPrincipal Jwt jwt) {
        if (!database.changeStatus(id, Long.parseLong(jwt.getSubject()), input.status().name())) {
            // Чужую задачу изменять нельзя.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return input;
    }
}
