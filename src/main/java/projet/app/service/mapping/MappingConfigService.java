package projet.app.service.mapping;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.MappingConfigRequest;
import projet.app.dto.MappingGroupSummaryResponse;
import projet.app.entity.staging.MappingConfig;
import projet.app.repository.staging.MappingConfigRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MappingConfigService {

    private final MappingConfigRepository mappingConfigRepository;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<MappingConfig> findAll() {
        return mappingConfigRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<MappingConfig> findByConfigGroupNumber(Integer configGroupNumber) {
        return mappingConfigRepository.findByConfigGroupNumberOrderByTableTargetAscIdAsc(configGroupNumber);
    }

    @Transactional(readOnly = true)
    public List<MappingGroupSummaryResponse> findGroupSummaries() {
        Map<Integer, Integer> countsByGroup = new LinkedHashMap<>();
        for (MappingConfig config : mappingConfigRepository.findAllByOrderByConfigGroupNumberAscIdAsc()) {
            countsByGroup.merge(config.getConfigGroupNumber(), 1, Integer::sum);
        }

        List<MappingGroupSummaryResponse> summaries = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : countsByGroup.entrySet()) {
            summaries.add(new MappingGroupSummaryResponse(entry.getKey(), entry.getValue()));
        }
        return summaries;
    }

    @Transactional(readOnly = true)
    public MappingConfig findById(Long id) {
        return mappingConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mapping config not found with id: " + id));
    }

    @Transactional
    public MappingConfig create(MappingConfigRequest request) {
        syncMappingConfigPrimaryKeySequence();

        MappingConfig entity = MappingConfig.builder()
                .tableSource(request.getTableSource())
                .tableTarget(request.getTableTarget())
                .columnSource(request.getColumnSource())
                .columnTarget(request.getColumnTarget())
                .configGroupNumber(request.getConfigGroupNumber())
                .build();

        return mappingConfigRepository.save(entity);
    }

    @Transactional
    public List<MappingConfig> createMany(List<MappingConfigRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("At least one mapping config is required");
        }

        syncMappingConfigPrimaryKeySequence();

        List<MappingConfig> entities = new ArrayList<>();
        for (MappingConfigRequest request : requests) {
            entities.add(MappingConfig.builder()
                    .tableSource(request.getTableSource())
                    .tableTarget(request.getTableTarget())
                    .columnSource(request.getColumnSource())
                    .columnTarget(request.getColumnTarget())
                    .configGroupNumber(request.getConfigGroupNumber())
                    .build());
        }

        return mappingConfigRepository.saveAll(entities);
    }

    @Transactional
    public MappingConfig update(Long id, MappingConfigRequest request) {
        MappingConfig existing = findById(id);

        existing.setTableSource(request.getTableSource());
        existing.setTableTarget(request.getTableTarget());
        existing.setColumnSource(request.getColumnSource());
        existing.setColumnTarget(request.getColumnTarget());
        existing.setConfigGroupNumber(request.getConfigGroupNumber());

        return mappingConfigRepository.save(existing);
    }

    @Transactional
    public List<MappingConfig> replaceByConfigGroupNumber(Integer configGroupNumber, List<MappingConfigRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("At least one mapping is required to replace a group");
        }

        for (MappingConfigRequest request : requests) {
            if (request.getConfigGroupNumber() == null) {
                throw new IllegalArgumentException("configGroupNumber is required for all mappings");
            }
            if (!configGroupNumber.equals(request.getConfigGroupNumber())) {
                throw new IllegalArgumentException(
                        "Request configGroupNumber mismatch. Path value is " + configGroupNumber
                );
            }
        }

        mappingConfigRepository.deleteByConfigGroupNumber(configGroupNumber);
    syncMappingConfigPrimaryKeySequence();

        List<MappingConfig> newMappings = new ArrayList<>();
        for (MappingConfigRequest request : requests) {
            newMappings.add(MappingConfig.builder()
                    .tableSource(request.getTableSource())
                    .tableTarget(request.getTableTarget())
                    .columnSource(request.getColumnSource())
                    .columnTarget(request.getColumnTarget())
                    .configGroupNumber(configGroupNumber)
                    .build());
        }

        mappingConfigRepository.saveAll(newMappings);
        return mappingConfigRepository.findByConfigGroupNumberOrderByTableTargetAscIdAsc(configGroupNumber);
    }

    @Transactional
    public void delete(Long id) {
        if (!mappingConfigRepository.existsById(id)) {
            throw new IllegalArgumentException("Mapping config not found with id: " + id);
        }

        mappingConfigRepository.deleteById(id);
    }

    @Transactional
    public void deleteByConfigGroupNumber(Integer configGroupNumber) {
        if (!mappingConfigRepository.existsByConfigGroupNumber(configGroupNumber)) {
            throw new IllegalArgumentException(
                    "No mapping configs found with configGroupNumber: " + configGroupNumber
            );
        }
        mappingConfigRepository.deleteByConfigGroupNumber(configGroupNumber);
    }

    private void syncMappingConfigPrimaryKeySequence() {
        jdbcTemplate.queryForObject(
                """
                SELECT setval(
                    pg_get_serial_sequence('mapping.mapping_config', 'id'),
                    COALESCE((SELECT MAX(id) FROM mapping.mapping_config), 1),
                    COALESCE((SELECT MAX(id) FROM mapping.mapping_config), 0) > 0
                )
                """,
                Long.class
        );
    }
}
