package projet.app.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import projet.app.ai.memory.entity.ChatSessionEntity;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionDTO {

    private UUID id;
    private String userId;
    private String title;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastMessageAt;

    public static ChatSessionDTO from(ChatSessionEntity e) {
        return ChatSessionDTO.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .title(e.getTitle())
                .status(e.getStatus())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .lastMessageAt(e.getLastMessageAt())
                .build();
    }
}
