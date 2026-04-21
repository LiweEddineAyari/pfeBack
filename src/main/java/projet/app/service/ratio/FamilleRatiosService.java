package projet.app.service.ratio;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.RatioReferenceRequestDTO;
import projet.app.dto.RatioReferenceResponseDTO;
import projet.app.entity.mapping.FamilleRatios;
import projet.app.exception.FamilleRatiosNotFoundException;
import projet.app.repository.mapping.FamilleRatiosRepository;
import projet.app.repository.mapping.RatiosConfigRepository;

import java.util.List;

@Service
public class FamilleRatiosService {

    private final FamilleRatiosRepository familleRatiosRepository;
    private final RatiosConfigRepository ratiosConfigRepository;

    public FamilleRatiosService(
            FamilleRatiosRepository familleRatiosRepository,
            RatiosConfigRepository ratiosConfigRepository
    ) {
        this.familleRatiosRepository = familleRatiosRepository;
        this.ratiosConfigRepository = ratiosConfigRepository;
    }

    @Transactional
    public RatioReferenceResponseDTO create(RatioReferenceRequestDTO request) {
        String normalizedName = normalizeName(request.getName());
        if (familleRatiosRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new IllegalArgumentException("Famille ratios already exists for name: " + normalizedName);
        }

        FamilleRatios saved = familleRatiosRepository.save(FamilleRatios.builder()
                .name(normalizedName)
                .build());

        return toResponse(saved);
    }

    @Transactional
    public RatioReferenceResponseDTO update(Long id, RatioReferenceRequestDTO request) {
        FamilleRatios existing = familleRatiosRepository.findById(id)
                .orElseThrow(() -> new FamilleRatiosNotFoundException(id));

        String normalizedName = normalizeName(request.getName());
        if (familleRatiosRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new IllegalArgumentException("Famille ratios already exists for name: " + normalizedName);
        }

        existing.setName(normalizedName);
        FamilleRatios updated = familleRatiosRepository.save(existing);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<RatioReferenceResponseDTO> list() {
        return familleRatiosRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RatioReferenceResponseDTO getById(Long id) {
        FamilleRatios entity = familleRatiosRepository.findById(id)
                .orElseThrow(() -> new FamilleRatiosNotFoundException(id));
        return toResponse(entity);
    }

    @Transactional
    public void deleteById(Long id) {
        FamilleRatios entity = familleRatiosRepository.findById(id)
                .orElseThrow(() -> new FamilleRatiosNotFoundException(id));

        if (ratiosConfigRepository.existsByFamilleId(id)) {
            throw new IllegalArgumentException("Cannot delete famille ratios id " + id + " because it is referenced by ratios config");
        }

        familleRatiosRepository.delete(entity);
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private RatioReferenceResponseDTO toResponse(FamilleRatios entity) {
        return RatioReferenceResponseDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .build();
    }
}