package projet.app.service.ratio;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.RatioReferenceRequestDTO;
import projet.app.dto.RatioReferenceResponseDTO;
import projet.app.entity.mapping.CategorieRatios;
import projet.app.exception.CategorieRatiosNotFoundException;
import projet.app.repository.mapping.CategorieRatiosRepository;
import projet.app.repository.mapping.RatiosConfigRepository;

import java.util.List;

@Service
public class CategorieRatiosService {

    private final CategorieRatiosRepository categorieRatiosRepository;
    private final RatiosConfigRepository ratiosConfigRepository;

    public CategorieRatiosService(
            CategorieRatiosRepository categorieRatiosRepository,
            RatiosConfigRepository ratiosConfigRepository
    ) {
        this.categorieRatiosRepository = categorieRatiosRepository;
        this.ratiosConfigRepository = ratiosConfigRepository;
    }

    @Transactional
    public RatioReferenceResponseDTO create(RatioReferenceRequestDTO request) {
        String normalizedName = normalizeName(request.getName());
        if (categorieRatiosRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new IllegalArgumentException("Categorie ratios already exists for name: " + normalizedName);
        }

        CategorieRatios saved = categorieRatiosRepository.save(CategorieRatios.builder()
                .name(normalizedName)
                .build());

        return toResponse(saved);
    }

    @Transactional
    public RatioReferenceResponseDTO update(Long id, RatioReferenceRequestDTO request) {
        CategorieRatios existing = categorieRatiosRepository.findById(id)
                .orElseThrow(() -> new CategorieRatiosNotFoundException(id));

        String normalizedName = normalizeName(request.getName());
        if (categorieRatiosRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, id)) {
            throw new IllegalArgumentException("Categorie ratios already exists for name: " + normalizedName);
        }

        existing.setName(normalizedName);
        CategorieRatios updated = categorieRatiosRepository.save(existing);
        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<RatioReferenceResponseDTO> list() {
        return categorieRatiosRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RatioReferenceResponseDTO getById(Long id) {
        CategorieRatios entity = categorieRatiosRepository.findById(id)
                .orElseThrow(() -> new CategorieRatiosNotFoundException(id));
        return toResponse(entity);
    }

    @Transactional
    public void deleteById(Long id) {
        CategorieRatios entity = categorieRatiosRepository.findById(id)
                .orElseThrow(() -> new CategorieRatiosNotFoundException(id));

        if (ratiosConfigRepository.existsByCategorieId(id)) {
            throw new IllegalArgumentException("Cannot delete categorie ratios id " + id + " because it is referenced by ratios config");
        }

        categorieRatiosRepository.delete(entity);
    }

    private String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    private RatioReferenceResponseDTO toResponse(CategorieRatios entity) {
        return RatioReferenceResponseDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .build();
    }
}