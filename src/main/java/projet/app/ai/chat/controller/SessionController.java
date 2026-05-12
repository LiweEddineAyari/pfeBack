package projet.app.ai.chat.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import projet.app.ai.chat.dto.ChatMessageDTO;
import projet.app.ai.chat.dto.ChatSessionDTO;
import projet.app.ai.chat.dto.ChatSessionTitleUpdateDTO;
import projet.app.ai.chat.service.SessionService;
import projet.app.ai.memory.entity.ChatSessionEntity;
import projet.app.ai.memory.repository.ChatMessageRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read / list / archive operations over chat sessions and their persisted messages.
 * The streaming chat itself lives in {@link AiChatController}.
 */
@RestController
@RequestMapping({"/ai/sessions", "/api/ai/sessions"})
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final ChatMessageRepository messageRepository;

    @PostMapping
    public ResponseEntity<ChatSessionDTO> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        ChatSessionEntity session = sessionService.createSession(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ChatSessionDTO.from(session));
    }

    @GetMapping
    public ResponseEntity<Page<ChatSessionDTO>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = (userIdHeader == null || userIdHeader.isBlank())
                ? "anonymous" : userIdHeader.trim();
        return ResponseEntity.ok(sessionService.listForUser(userId, PageRequest.of(page, size)));
    }

        @GetMapping("/user/{userId}")
        public ResponseEntity<Page<ChatSessionDTO>> listByUserId(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String normalized = (userId == null || userId.isBlank())
            ? "anonymous" : userId.trim();
        return ResponseEntity.ok(sessionService.listForUser(normalized, PageRequest.of(page, size)));
        }

    @GetMapping("/{id}")
    public ResponseEntity<ChatSessionDTO> getOne(@PathVariable UUID id) {
        Optional<ChatSessionEntity> opt = sessionService.findById(id);
        return opt.map(s -> ResponseEntity.ok(ChatSessionDTO.from(s)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<ChatMessageDTO>> messages(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        if (sessionService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<ChatMessageDTO> messages = messageRepository
                .findBySessionIdOrderBySequenceNoAsc(id, PageRequest.of(page, size))
                .map(ChatMessageDTO::from)
                .getContent();
        return ResponseEntity.ok(messages);
    }

    @PatchMapping("/{id}/title")
    public ResponseEntity<ChatSessionDTO> renameTitle(
            @PathVariable UUID id,
            @Valid @RequestBody ChatSessionTitleUpdateDTO request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<ChatSessionEntity> opt = sessionService.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        sessionService.updateTitle(id, request.getTitle().trim());
        return sessionService.findById(id)
                .map(s -> ResponseEntity.ok(ChatSessionDTO.from(s)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        if (sessionService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        sessionService.archive(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (sessionService.findById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        sessionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
