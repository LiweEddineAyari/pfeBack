package projet.app.service.ratio;

import java.util.List;

public class ParameterResult {

    private final boolean isMultiRow;
    private final Double value;
    private final List<RowValue> rows;

    private ParameterResult(boolean isMultiRow, Double value, List<RowValue> rows) {
        this.isMultiRow = isMultiRow;
        this.value = value;
        this.rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static ParameterResult scalar(Double value) {
        return new ParameterResult(false, value == null ? 0d : value, List.of());
    }

    public static ParameterResult multiRow(List<RowValue> rows) {
        return new ParameterResult(true, null, rows == null ? List.of() : rows);
    }

    public boolean isMultiRow() {
        return isMultiRow;
    }

    public boolean isScalar() {
        return !isMultiRow;
    }

    public Double getValue() {
        return value;
    }

    public List<RowValue> getRows() {
        return rows;
    }

    public EvaluationMode mode() {
        return isMultiRow ? EvaluationMode.DIMENSIONAL : EvaluationMode.SCALAR;
    }
}
