package projet.app.service.connector;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import projet.app.dto.DbType;

@Component
@RequiredArgsConstructor
public class ConnectorFactory {

    private final PostgresConnector postgresConnector;
    private final MySqlConnector mySqlConnector;
    private final SqlServerConnector sqlServerConnector;
    private final OracleConnector oracleConnector;

    public DataSourceConnector getConnector(DbType dbType) {
        return switch (dbType) {
            case POSTGRES -> postgresConnector;
            case MYSQL -> mySqlConnector;
            case SQLSERVER -> sqlServerConnector;
            case ORACLE -> oracleConnector;
        };
    }
}
