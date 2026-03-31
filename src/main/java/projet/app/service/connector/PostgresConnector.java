package projet.app.service.connector;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import projet.app.dto.DbType;

@Component
public class PostgresConnector extends AbstractJdbcConnector {

    public PostgresConnector(DynamicConnectionFactory connectionFactory, JdbcTemplate jdbcTemplate) {
        super(connectionFactory, jdbcTemplate);
    }

    @Override
    protected DbType supportedDbType() {
        return DbType.POSTGRES;
    }
}
