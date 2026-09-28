package dev.sendtrace.message;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView submit(@RequestBody @Valid SubmitMessageRequest req,
                               @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return messages.submit(req.channel(), req.recipient(), req.body(), idempotencyKey, req);
    }

    @GetMapping("/{id}")
    public MessageView get(@PathVariable UUID id) {
        return messages.get(id);
    }

    public record SubmitMessageRequest(
        @NotNull Channel channel,
        @NotBlank String recipient,
        @NotBlank String body
    ) {}
}
