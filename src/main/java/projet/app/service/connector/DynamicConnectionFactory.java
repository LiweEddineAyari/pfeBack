package projet.app.service.connector;

import org.springframework.stereotype.Service;
import projet.app.dto.DbConnectionRequest;
import projet.app.dto.DbType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

@Service
public class DynamicConnectionFactory {

    public Connection createConnection(DbConnectionRequest req) {
        try {
            String url = buildUrl(req);
            Class.forName(driverClass(req.getDbType()));
            return DriverManager.getConnection(url, req.getUsername(), req.getPassword());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver not found for dbType=" + req.getDbType(), e);
        } catch (SQLException e) {
            throw new IllegalArgumentException("Failed to connect to source database", e);
        }
    }

    private String buildUrl(DbConnectionRequest req) {
        return switch (req.getDbType()) {
            case POSTGRES -> "jdbc:postgresql://" + req.getHost() + ":" + req.getPort() + "/" + req.getDatabase();
            case MYSQL -> "jdbc:mysql://" + req.getHost() + ":" + req.getPort() + "/" + req.getDatabase();
            case SQLSERVER -> "jdbc:sqlserver://" + req.getHost() + ":" + req.getPort() + ";databaseName=" + req.getDatabase();
            case ORACLE -> "jdbc:oracle:thin:@" + req.getHost() + ":" + req.getPort() + ":" + req.getDatabase();
        };
    }

    private String driverClass(DbType dbType) {
        return switch (dbType) {
            case POSTGRES -> "org.postgresql.Driver";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case SQLSERVER -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case ORACLE -> "oracle.jdbc.OracleDriver";
        };
    }
}
