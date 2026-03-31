package projet.app.service.connector;

import projet.app.dto.ColumnMeta;
import projet.app.dto.DbConnectionRequest;
import projet.app.dto.LoadRequest;

import java.util.List;

public interface DataSourceConnector {
    List<ColumnMeta> getColumns(DbConnectionRequest req);
    void loadData(LoadRequest req);
}
