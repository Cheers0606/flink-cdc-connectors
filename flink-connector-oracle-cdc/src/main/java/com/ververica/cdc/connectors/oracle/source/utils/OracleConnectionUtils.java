/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ververica.cdc.connectors.oracle.source.utils;

import org.apache.flink.util.FlinkRuntimeException;

import com.ververica.cdc.connectors.oracle.source.meta.offset.RedoLogOffset;
import io.debezium.config.Configuration;
import io.debezium.connector.oracle.OracleConnection;
import io.debezium.jdbc.JdbcConnection;
import io.debezium.relational.RelationalTableFilters;
import io.debezium.relational.TableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.debezium.config.CommonConnectorConfig.DATABASE_CONFIG_PREFIX;

/** Oracle connection Utilities. */
public class OracleConnectionUtils {

    private static final Logger LOG = LoggerFactory.getLogger(OracleConnectionUtils.class);

    /** Creates a new {@link OracleConnection}, but not open the connection. */
    public static OracleConnection createOracleConnection(Configuration dbzConfiguration) {
        return new OracleConnection(dbzConfiguration.subset(DATABASE_CONFIG_PREFIX, true), OracleConnectionUtils.class::getClassLoader);
    }

    /** Fetch current redoLog offsets in Oracle Server. */
    public static RedoLogOffset currentRedoLogOffset(JdbcConnection jdbc) {
        final String showCurrentScn = "SELECT CURRENT_SCN FROM V$DATABASE";
        try {
            return jdbc.queryAndMap(
                    showCurrentScn,
                    rs -> {
                        if (rs.next()) {
                            final String scn = rs.getString(1);
                            return new RedoLogOffset(scn);
                        } else {
                            throw new FlinkRuntimeException(
                                    "Cannot read the scn via '"
                                            + showCurrentScn
                                            + "'. Make sure your server is correctly configured");
                        }
                    });
        } catch (SQLException e) {
            throw new FlinkRuntimeException(
                    "Cannot read the redo log position via '"
                            + showCurrentScn
                            + "'. Make sure your server is correctly configured",
                    e);
        }
    }

    public static List<TableId> listTables(
            JdbcConnection jdbcConnection, RelationalTableFilters tableFilters)
            throws SQLException {
        final List<TableId> capturedTableIds = new ArrayList<>();

        Set<TableId> tableIdSet = jdbcConnection.readAllTableNames(null);
        for (TableId tableId : tableIdSet) {
            if (tableFilters.dataCollectionFilter().isIncluded(tableId)) {
                capturedTableIds.add(tableId);
                LOG.info("\t including '{}' for further processing", tableId);
            } else {
                LOG.info("\t '{}' is filtered out of capturing", tableId);
            }
        }

        return capturedTableIds;
    }

    // --------------------------------------------------------------------------------------------
}
