package projet.app.ai.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequestDTO {

    /** {@code null} → a brand-new session is created server-side. */
    private String sessionId;

    @NotBlank(message = "message is required")
    private String message;

    /** Optional locale hint ({@code "fr"}, {@code "ar"}, {@code "en"}). Currently advisory only. */
    private String userLocale;
}
