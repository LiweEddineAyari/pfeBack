package projet.app.service.transform.tiers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.entity.staging.StgTiersRaw;
import projet.app.repository.staging.StgTiersRawRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Transform service for STG_TIERS_RAW staging table.
 * Applies business transformations after data quality checks.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TiersTransformService {

    private final StgTiersRawRepository tiersRepository;

    private static final int BATCH_SIZE = 500;

    /**
     * Transform all rows in stg_tiers_raw:
     * 1. agenteco: map source value to regulateur value
     * 2. residencenum: set numeric regulateur code from residence ISO code
     *
     * @return number of rows transformed
     */
    @Transactional
    public int transformStagingTable() {
        log.info("Starting transformation of stg_tiers_raw");

        List<StgTiersRaw> all = tiersRepository.findAll();
        log.info("Found {} rows to transform", all.size());

        int transformed = 0;
        List<StgTiersRaw> batch = new ArrayList<>(BATCH_SIZE);

        for (StgTiersRaw row : all) {
            boolean changed = false;

            // Transform agenteco: source value -> regulateur value
            Integer agentEcoTransformed = AgentEcoTransformer.transform(row.getAgenteco());
            if (agentEcoTransformed != null) {
                row.setAgenteco(String.valueOf(agentEcoTransformed));
                changed = true;
            }

            // Transform residence -> residencenum
            Integer residenceNum = ResidenceTransformer.transform(row.getResidence());
            if (residenceNum != null) {
                row.setResidencenum(residenceNum);
                changed = true;
            }

            // Transform sectionactivite: source / 10 -> valeur regulateur
            Integer sectionTransformed = SectionActiviteTransformer.transform(row.getSectionactivite());
            if (sectionTransformed != null) {
                row.setSectionactivite(String.valueOf(sectionTransformed));
                changed = true;
            }

            if (changed) {
                batch.add(row);
                transformed++;
            }

            if (batch.size() >= BATCH_SIZE) {
                tiersRepository.saveAll(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            tiersRepository.saveAll(batch);
        }

        log.info("Transformation completed. {} rows transformed", transformed);
        return transformed;
    }
}
