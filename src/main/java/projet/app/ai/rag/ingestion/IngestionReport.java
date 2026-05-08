package projet.app.ai.rag.ingestion;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngestionReport {

    private int rowsParsed;
    private int chunksProduced;
    private int chunksPersisted;
    private int chunksFailed;
    private List<String> warnings;
    private String status;
    private Instant completedAt;
}
