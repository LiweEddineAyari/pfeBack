package projet.app.service.connector;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import projet.app.dto.ColumnMeta;
import projet.app.dto.DbConnectionRequest;
import projet.app.dto.IngestionType;
import projet.app.dto.LoadRequest;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class AbstractJdbcConnector implements DataSourceConnector {

    private static final int BATCH_SIZE = 500;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Pattern TABLE_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)?$");

    private final DynamicConnectionFactory connectionFactory;
    private final JdbcTemplate jdbcTemplate;

    protected abstract projet.app.dto.DbType supportedDbType();

    @Override
    public List<ColumnMeta> getColumns(DbConnectionRequest req) {
        assertDbType(req);
        validateTableName(req.getTable());

        try (Connection connection = connectionFactory.createConnection(req)) {
            if (!tableExists(connection, req.getTable())) {
                throw new IllegalArgumentException("Table not found: " + req.getTable());
            }

            List<ColumnMeta> columns = new ArrayList<>();
            String[] schemaAndTable = splitSchemaAndTable(req.getTable());
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getColumns(connection.getCatalog(), schemaAndTable[0], schemaAndTable[1], null)) {
                while (rs.next()) {
                    String nullable = rs.getString("IS_NULLABLE");
                    columns.add(new ColumnMeta(
                            rs.getString("COLUMN_NAME"),
                            rs.getString("TYPE_NAME"),
                            "YES".equalsIgnoreCase(nullable)
                    ));
                }
            }
            return columns;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch columns from source table", e);
        }
    }

    @Override
    public void loadData(LoadRequest req) {
        DbConnectionRequest connectionRequest = req.getConnection();
        assertDbType(connectionRequest);
        validateTableName(connectionRequest.getTable());

        Map<String, String> mapping = req.getMapping() == null ? Map.of() : req.getMapping();
        if (mapping.isEmpty()) {
            throw new IllegalArgumentException("Invalid mapping: at least one source-to-target mapping is required");
        }

        javax.sql.DataSource targetDataSource = Objects.requireNonNull(
            jdbcTemplate.getDataSource(),
            "Target datasource is not configured"
        );

           Connection targetConnection = DataSourceUtils.getConnection(targetDataSource);
           try (Connection sourceConnection = connectionFactory.createConnection(connectionRequest);
               Statement sourceStatement = sourceConnection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {

            String targetTable = req.getTargetTable();
            validateTableName(targetTable);
            ensureTargetTableExists(targetConnection, req.getType(), targetTable);

            if (!tableExists(sourceConnection, connectionRequest.getTable())) {
                throw new IllegalArgumentException("Table not found: " + connectionRequest.getTable());
            }

            sourceStatement.setFetchSize(BATCH_SIZE);

            String selectSql = "SELECT * FROM " + connectionRequest.getTable();
            try (ResultSet resultSet = sourceStatement.executeQuery(selectSql)) {
                ResultSetMetaData sourceMeta = resultSet.getMetaData();
                validateSourceColumns(mapping, sourceMeta);

                Map<String, Integer> targetTypeByColumn = targetColumnTypes(targetConnection, targetTable);

                List<String> sourceColumns = new ArrayList<>(mapping.keySet());
                List<String> targetColumns = sourceColumns.stream().map(mapping::get).collect(Collectors.toCollection(ArrayList::new));

                boolean appendDerivedDateBal = req.getType() == IngestionType.COMPTA
                        && targetColumns.stream().noneMatch("date_bal"::equalsIgnoreCase);
                if (appendDerivedDateBal) {
                    targetColumns.add("date_bal");
                }

                validateTargetColumns(targetColumns, targetTypeByColumn);

                String insertSql = buildInsertSql(targetTable, targetColumns);
                try (PreparedStatement insertStatement = targetConnection.prepareStatement(insertSql)) {
                    int batchCount = 0;
                    while (resultSet.next()) {
                        int index = 1;
                        for (String sourceColumn : sourceColumns) {
                            String targetColumn = mapping.get(sourceColumn);
                            Object value = resultSet.getObject(sourceColumn);
                            setPreparedValue(insertStatement, index++, value, targetTypeByColumn.get(targetColumn.toLowerCase()));
                        }

                        if (appendDerivedDateBal) {
                            LocalDate dateBal = LocalDate.parse(req.getDateBal(), DATE_FORMATTER);
                            insertStatement.setObject(index, dateBal);
                        }

                        insertStatement.addBatch();
                        batchCount++;
                        if (batchCount % BATCH_SIZE == 0) {
                            insertStatement.executeBatch();
                        }
                    }
                    if (batchCount % BATCH_SIZE != 0) {
                        insertStatement.executeBatch();
                    }
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to load data from source database", e);
        } finally {
            DataSourceUtils.releaseConnection(targetConnection, targetDataSource);
        }
    }

    private void ensureTargetTableExists(Connection connection, IngestionType type, String targetTable) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS staging");

            if ("staging.stg_tiers_raw".equalsIgnoreCase(targetTable) || type == IngestionType.TIERS) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS staging.stg_tiers_raw (
                        id BIGSERIAL PRIMARY KEY,
                        idtiers TEXT,
                        nomprenom TEXT,
                        raisonsoc TEXT,
                        residence TEXT,
                        agenteco TEXT,
                        sectionactivite TEXT,
                        chiffreaffaires TEXT,
                        nationalite TEXT,
                        douteux TEXT,
                        datdouteux TEXT,
                        grpaffaires INTEGER,
                        nomgrpaffaires TEXT,
                        residencenum INTEGER
                    )
                    """);
            }

            if ("staging.stg_contrat_raw".equalsIgnoreCase(targetTable) || type == IngestionType.CONTRAT) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS staging.stg_contrat_raw (
                        id BIGSERIAL PRIMARY KEY,
                        idcontrat TEXT,
                        agence TEXT,
                        devise TEXT,
                        ancienneteimpaye TEXT,
                        objetfinance TEXT,
                        typcontrat TEXT,
                        datouv TEXT,
                        datech TEXT,
                        idtiers TEXT,
                        tauxcontrat TEXT,
                        actif TEXT
                    )
                    """);
            }

            if ("staging.stg_compta_raw".equalsIgnoreCase(targetTable) || type == IngestionType.COMPTA) {
                statement.execute("""
                    CREATE TABLE IF NOT EXISTS staging.stg_compta_raw (
                        id BIGSERIAL PRIMARY KEY,
                        agence TEXT,
                        devise TEXT,
                        compte TEXT,
                        chapitre TEXT,
                        libellecompte TEXT,
                        idtiers TEXT,
                        soldeorigine TEXT,
                        soldeconvertie TEXT,
                        devisebbnq TEXT,
                        cumulmvtdb TEXT,
                        cumulmvtcr TEXT,
                        soldeinitdebmois TEXT,
                        idcontrat TEXT,
                        amount TEXT,
                        actif TEXT,
                        date_bal TEXT
                    )
                    """);
            }
        }
    }

    private void assertDbType(DbConnectionRequest req) {
        if (req == null || req.getDbType() == null) {
            throw new IllegalArgumentException("dbType is required");
        }
        if (req.getDbType() != supportedDbType()) {
            throw new IllegalArgumentException("Unsupported dbType for this connector: " + req.getDbType());
        }
    }

    private void validateSourceColumns(Map<String, String> mapping, ResultSetMetaData metaData) throws SQLException {
        List<String> sourceColumns = new ArrayList<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            sourceColumns.add(metaData.getColumnLabel(i).toLowerCase());
        }

        for (String mappedSource : mapping.keySet()) {
            if (!sourceColumns.contains(mappedSource.toLowerCase())) {
                throw new IllegalArgumentException("Invalid mapping: source column not found: " + mappedSource);
            }
        }
    }

    private Map<String, Integer> targetColumnTypes(Connection connection, String table) throws SQLException {
        String[] schemaAndTable = splitSchemaAndTable(table);
        DatabaseMetaData meta = connection.getMetaData();
        Map<String, Integer> result = new HashMap<>();

        // Fast path: query metadata from an empty result set.
        // This avoids expensive catalog scans on some drivers.
        collectColumnsFromSelectMetadata(connection, table, result);

        // Fallback path: JDBC catalog metadata for drivers where the fast path is insufficient.
        if (result.isEmpty()) {
            collectColumns(meta, connection.getCatalog(), schemaAndTable[0], schemaAndTable[1], result);
        }
        if (result.isEmpty()) {
            collectColumns(meta, null, schemaAndTable[0], schemaAndTable[1], result);
        }
        if (result.isEmpty()) {
            collectColumns(meta, null, schemaAndTable[0], schemaAndTable[1].toLowerCase(), result);
        }
        if (result.isEmpty()) {
            collectColumns(meta, null, schemaAndTable[0], schemaAndTable[1].toUpperCase(), result);
        }

        return result;
    }

    private void collectColumns(
            DatabaseMetaData meta,
            String catalog,
            String schema,
            String table,
            Map<String, Integer> sink) throws SQLException {
        if (table == null || table.isBlank()) {
            return;
        }

        try (ResultSet rs = meta.getColumns(catalog, schema, table, null)) {
            while (rs.next()) {
                sink.put(rs.getString("COLUMN_NAME").toLowerCase(), rs.getInt("DATA_TYPE"));
            }
        }
    }

    private void collectColumnsFromSelectMetadata(
            Connection connection,
            String qualifiedTable,
            Map<String, Integer> sink) throws SQLException {
        String sql = "SELECT * FROM " + qualifiedTable + " WHERE 1=0";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                sink.put(normalizeColumnName(md.getColumnLabel(i)), md.getColumnType(i));
            }
        }
    }

    private void validateTargetColumns(List<String> targetColumns, Map<String, Integer> targetTypeByColumn) {
        for (String column : targetColumns) {
            if (!targetTypeByColumn.containsKey(normalizeColumnName(column))) {
                throw new IllegalArgumentException("Invalid mapping: target column not found: " + column);
            }
        }
    }

    private String buildInsertSql(String table, List<String> targetColumns) {
        String joinedColumns = String.join(", ", targetColumns);
        String placeholders = targetColumns.stream().map(c -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + table + " (" + joinedColumns + ") VALUES (" + placeholders + ")";
    }

    private void setPreparedValue(PreparedStatement statement, int index, Object value, Integer targetSqlType) throws SQLException {
        if (value == null) {
            statement.setObject(index, null);
            return;
        }

        if (targetSqlType == null) {
            statement.setObject(index, value);
            return;
        }

        String stringValue = value.toString();
        switch (targetSqlType) {
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> statement.setInt(index, Integer.parseInt(stringValue));
            case Types.BIGINT -> statement.setLong(index, Long.parseLong(stringValue));
            case Types.DECIMAL, Types.NUMERIC -> statement.setBigDecimal(index, new java.math.BigDecimal(stringValue));
            case Types.DOUBLE, Types.FLOAT, Types.REAL -> statement.setDouble(index, Double.parseDouble(stringValue));
            case Types.BOOLEAN, Types.BIT -> statement.setBoolean(index, Boolean.parseBoolean(stringValue));
            case Types.DATE -> statement.setDate(index, java.sql.Date.valueOf(LocalDate.parse(stringValue)));
            default -> statement.setObject(index, value);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        String[] schemaAndTable = splitSchemaAndTable(tableName);
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(connection.getCatalog(), schemaAndTable[0], schemaAndTable[1], new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }

        if (schemaAndTable[0] == null) {
            try (ResultSet rs = meta.getTables(connection.getCatalog(), null, schemaAndTable[1], new String[]{"TABLE"})) {
                return rs.next();
            }
        }

        return false;
    }

    private String normalizeColumnName(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private String[] splitSchemaAndTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name is required");
        }

        String[] parts = tableName.split("\\.");
        if (parts.length == 2) {
            return new String[]{parts[0], parts[1]};
        }
        return new String[]{null, parts[0]};
    }

    protected void validateTableName(String tableName) {
        if (tableName == null || tableName.isBlank() || !TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid table name");
        }
    }
}
