package projet.app.service.mapping;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.MappingConfigRequest;
import projet.app.entity.staging.MappingConfig;
import projet.app.repository.staging.MappingConfigRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MappingConfigService {

    private final MappingConfigRepository mappingConfigRepository;

    @Transactional(readOnly = true)
    public List<MappingConfig> findAll() {
        return mappingConfigRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<MappingConfig> findByConfigGroupNumber(Integer configGroupNumber) {
        return mappingConfigRepository.findByConfigGroupNumberOrderByTableTargetAscIdAsc(configGroupNumber);
    }

    @Transactional(readOnly = true)
    public MappingConfig findById(Long id) {
        return mappingConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mapping config not found with id: " + id));
    }

    @Transactional
    public MappingConfig create(MappingConfigRequest request) {
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
    public void delete(Long id) {
        if (!mappingConfigRepository.existsById(id)) {
            throw new IllegalArgumentException("Mapping config not found with id: " + id);
        }

        mappingConfigRepository.deleteById(id);
    }
}
