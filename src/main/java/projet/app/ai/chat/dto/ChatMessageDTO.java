package projet.app.ai.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import projet.app.ai.memory.entity.ChatMessageEntity;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    private UUID id;
    private UUID sessionId;
    private String role;
    private String content;
    private String toolName;
    private String toolInput;
    private String toolOutput;
    private Long sequenceNo;
    private Instant createdAt;

    public static ChatMessageDTO from(ChatMessageEntity e) {
        return ChatMessageDTO.builder()
                .id(e.getId())
                .sessionId(e.getSessionId())
                .role(e.getRole())
                .content(e.getContent())
                .toolName(e.getToolName())
                .toolInput(e.getToolInput())
                .toolOutput(e.getToolOutput())
                .sequenceNo(e.getSequenceNo())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
