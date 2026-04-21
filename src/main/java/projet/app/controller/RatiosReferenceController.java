package projet.app.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import projet.app.dto.RatioReferenceRequestDTO;
import projet.app.dto.RatioReferenceResponseDTO;
import projet.app.service.ratio.CategorieRatiosService;
import projet.app.service.ratio.FamilleRatiosService;

import java.util.List;

@RestController
@RequestMapping("/ratios")
public class RatiosReferenceController {

    private final FamilleRatiosService familleRatiosService;
    private final CategorieRatiosService categorieRatiosService;

    public RatiosReferenceController(
            FamilleRatiosService familleRatiosService,
            CategorieRatiosService categorieRatiosService
    ) {
        this.familleRatiosService = familleRatiosService;
        this.categorieRatiosService = categorieRatiosService;
    }

    @PostMapping("/families")
    public ResponseEntity<RatioReferenceResponseDTO> createFamily(@Valid @RequestBody RatioReferenceRequestDTO request) {
        RatioReferenceResponseDTO response = familleRatiosService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/families")
    public ResponseEntity<List<RatioReferenceResponseDTO>> listFamilies() {
        return ResponseEntity.ok(familleRatiosService.list());
    }

    @GetMapping("/families/{id}")
    public ResponseEntity<RatioReferenceResponseDTO> getFamilyById(@PathVariable Long id) {
        return ResponseEntity.ok(familleRatiosService.getById(id));
    }

    @PutMapping("/families/{id}")
    public ResponseEntity<RatioReferenceResponseDTO> updateFamily(
            @PathVariable Long id,
            @Valid @RequestBody RatioReferenceRequestDTO request
    ) {
        return ResponseEntity.ok(familleRatiosService.update(id, request));
    }

    @DeleteMapping("/families/{id}")
    public ResponseEntity<Void> deleteFamily(@PathVariable Long id) {
        familleRatiosService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/categories")
    public ResponseEntity<RatioReferenceResponseDTO> createCategory(@Valid @RequestBody RatioReferenceRequestDTO request) {
        RatioReferenceResponseDTO response = categorieRatiosService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<RatioReferenceResponseDTO>> listCategories() {
        return ResponseEntity.ok(categorieRatiosService.list());
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<RatioReferenceResponseDTO> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categorieRatiosService.getById(id));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<RatioReferenceResponseDTO> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody RatioReferenceRequestDTO request
    ) {
        return ResponseEntity.ok(categorieRatiosService.update(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categorieRatiosService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}