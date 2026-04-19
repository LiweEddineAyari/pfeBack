package projet.app.service.ratio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import projet.app.dto.BulkDeleteResponseDTO;
import projet.app.dto.RatioExecutionResponseDTO;
import projet.app.dto.RatioSimulationRequestDTO;
import projet.app.dto.RatioSimulationResponseDTO;
import projet.app.dto.RatiosConfigRequestDTO;
import projet.app.dto.RatiosConfigResponseDTO;

import java.time.LocalDate;
import java.util.List;

@Service
public class RatiosService {

    private final RatiosEngineService ratiosEngineService;

    public RatiosService(RatiosEngineService ratiosEngineService) {
        this.ratiosEngineService = ratiosEngineService;
    }

    @Transactional
    public RatiosConfigResponseDTO create(RatiosConfigRequestDTO request) {
        return ratiosEngineService.create(request);
    }

    @Transactional
    public RatiosConfigResponseDTO update(String code, RatiosConfigRequestDTO request) {
        return ratiosEngineService.update(code, request);
    }

    @Transactional(readOnly = true)
    public List<RatiosConfigResponseDTO> list() {
        return ratiosEngineService.list();
    }

    @Transactional(readOnly = true)
    public RatiosConfigResponseDTO getByCode(String code) {
        return ratiosEngineService.getByCode(code);
    }

    @Transactional
    public void deleteByCode(String code) {
        ratiosEngineService.deleteByCode(code);
    }

    @Transactional
    public BulkDeleteResponseDTO deleteManyByCodes(List<String> codes) {
        return ratiosEngineService.deleteManyByCodes(codes);
    }

    @Transactional(readOnly = true)
    public RatioSimulationResponseDTO simulate(RatioSimulationRequestDTO request) {
        return ratiosEngineService.simulate(request);
    }

    @Transactional(readOnly = true)
    public RatioExecutionResponseDTO executeByCodeAtDate(String code, LocalDate referenceDate) {
        return ratiosEngineService.executeByCodeAtDate(code, referenceDate);
    }
}
