package com.ververica.cdc.connectors.oracle.source.utils;

import com.ververica.cdc.connectors.oracle.utils.OracleTestUtils;
import io.debezium.config.Configuration;
import io.debezium.connector.oracle.OracleConnection;
import io.debezium.connector.oracle.OracleConnector;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.lifecycle.Startables;

import java.sql.SQLException;
import java.util.stream.Stream;

import static com.ververica.cdc.connectors.oracle.source.OraclePooledDataSourceFactory.JDBC_URL_PATTERN;
import static org.junit.Assert.*;

/**
 * @author Enoch on 2022/4/18
 */
public class OracleConnectionUtilsTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(OracleConnectionUtilsTest.class);
    private static final OracleContainer oracleContainer =
            OracleTestUtils.ORACLE_CONTAINER.withLogConsumer(new Slf4jLogConsumer(LOG));

    @BeforeClass
    public static void startContainers() {
        LOG.info("Starting containers...");
        Startables.deepStart(Stream.of(oracleContainer)).join();
        LOG.info("Containers are started.");
    }
    @Test
    public void createOracleConnection() throws SQLException {
        Configuration configuration = Configuration.create()
                .with("connector.class", OracleConnector.class.getCanonicalName())
                .with("database.port",oracleContainer.getOraclePort())
                .with("database.history.skip.unparseable.ddl","true")
                .with("table.include.list","debezium.PRODUCTS")
                .with("schema.include.list","debezium")
                .with("database.user",oracleContainer.getUsername())
                .with("database.hostname",oracleContainer.getHost())
                .with("database.password",oracleContainer.getPassword())
                .with("database.server.name","oracle_logminer")
                .with("database.dbname","debezium")
                .with("driver.class.name",oracleContainer.getDriverClassName())
                .with("url",oracleContainer.getJdbcUrl())
//                .with("url",String.format("%s:%s:%s", "192.168.33.3", "32928", "xe"))
                .build();
        OracleConnection oracleConnection = OracleConnectionUtils.createOracleConnection(configuration);
        System.out.println("oracleConnection.getCurrentScn() = " + oracleConnection.getCurrentScn());
    }
}