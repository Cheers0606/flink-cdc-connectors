package com.ververica.cdc.connectors.oracle.source.utils;

import io.debezium.config.Configuration;
import io.debezium.connector.oracle.OracleConnection;
import org.junit.Test;

import java.sql.SQLException;

import static com.ververica.cdc.connectors.oracle.source.OraclePooledDataSourceFactory.JDBC_URL_PATTERN;
import static org.junit.Assert.*;

/**
 * @author Enoch on 2022/4/18
 */
public class OracleConnectionUtilsTest {

    @Test
    public void createOracleConnection() throws SQLException {
        Configuration configuration = Configuration.create()
                .with("connector.class","io.debezium.connector.oracle.OracleConnector")
                .with("database.port","32928")
                .with("database.history.skip.unparseable.ddl","true")
                .with("table.include.list","debezium.PRODUCTS")
                .with("schema.include.list","debezium")
                .with("database.user","system")
                .with("database.hostname","192.168.33.3")
                .with("database.password","oracle")
                .with("database.server.name","oracle_logminer")
                .with("database.dbname","debezium")
                .with("driver.class.name","oracle.jdbc.OracleDriver")
                .with("url",String.format("%s:%s:%s", "192.168.33.3", "32928", "xe"))
                .build();
        OracleConnection oracleConnection = OracleConnectionUtils.createOracleConnection(configuration);
        System.out.println("oracleConnection.getCurrentScn() = " + oracleConnection.getCurrentScn());
    }
}