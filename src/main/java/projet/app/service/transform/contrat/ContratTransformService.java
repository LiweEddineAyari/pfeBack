package projet.app.service.transform.contrat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.entity.staging.StgContratRaw;
import projet.app.repository.staging.StgContratRawRepository;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContratTransformService {

    private final StgContratRawRepository contratRepository;

    private static final int BATCH_SIZE = 500;

    @Transactional
    public int transformStagingTable() {
        log.info("Starting transformation of stg_contrat_raw");

        List<StgContratRaw> all = contratRepository.findAll();
        log.info("Found {} rows to transform", all.size());

        int transformed = 0;
        List<StgContratRaw> batch = new ArrayList<>(BATCH_SIZE);

        for (StgContratRaw row : all) {
            boolean changed = false;

            // Transform objetfinance: source value -> regulateur value
            Integer objetTransformed = ObjetFinanceTransformer.transform(row.getObjetfinance());
            if (objetTransformed != null) {
                row.setObjetfinance(String.valueOf(objetTransformed));
                changed = true;
            }

            // Transform typcontrat: p -> PRET, d -> COMPTE ORDINAIRE
            String typTransformed = TypeContratTransformer.transform(row.getTypcontrat());
            if (typTransformed != null && !typTransformed.equals(row.getTypcontrat())) {
                row.setTypcontrat(typTransformed);
                changed = true;
            }

            // Transform datouv: ensure dd/MM/yyyy format
            String datOuvTransformed = DateTransformer.transform(row.getDatouv());
            if (datOuvTransformed != null && !datOuvTransformed.equals(row.getDatouv())) {
                row.setDatouv(datOuvTransformed);
                changed = true;
            }

            // Transform datech: ensure dd/MM/yyyy format
            String datEchTransformed = DateTransformer.transform(row.getDatech());
            if (datEchTransformed != null && !datEchTransformed.equals(row.getDatech())) {
                row.setDatech(datEchTransformed);
                changed = true;
            }

            if (changed) {
                batch.add(row);
                transformed++;
            }

            if (batch.size() >= BATCH_SIZE) {
                contratRepository.saveAll(batch);
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            contratRepository.saveAll(batch);
        }

        log.info("Transformation completed. {} rows transformed", transformed);
        return transformed;
    }
}
