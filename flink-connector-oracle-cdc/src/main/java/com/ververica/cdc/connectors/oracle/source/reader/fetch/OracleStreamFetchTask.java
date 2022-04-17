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

package com.ververica.cdc.connectors.oracle.source.reader.fetch;

import com.ververica.cdc.connectors.base.relational.JdbcSourceEventDispatcher;
import com.ververica.cdc.connectors.base.source.meta.split.SourceSplitBase;
import com.ververica.cdc.connectors.base.source.meta.split.StreamSplit;
import com.ververica.cdc.connectors.base.source.reader.external.FetchTask;
import com.ververica.cdc.connectors.oracle.source.meta.offset.RedoLogOffset;
import io.debezium.config.Configuration;
import io.debezium.connector.oracle.OracleConnection;
import io.debezium.connector.oracle.OracleConnectorConfig;
import io.debezium.connector.oracle.OracleDatabaseSchema;
import io.debezium.connector.oracle.OracleOffsetContext;
import io.debezium.connector.oracle.OracleStreamingChangeEventSourceMetrics;
import io.debezium.connector.oracle.logminer.LogMinerStreamingChangeEventSource;
import io.debezium.pipeline.ErrorHandler;
import io.debezium.pipeline.source.spi.ChangeEventSource;
import io.debezium.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.ververica.cdc.connectors.oracle.source.meta.offset.RedoLogOffset.NO_STOPPING_OFFSET;

/** The task to work for fetching data of Oracle table stream split. */
public class OracleStreamFetchTask implements FetchTask<SourceSplitBase> {

    private final StreamSplit split;
    private volatile boolean taskRunning = false;
    private OracleRedoLogSplitReadTask redoLogSplitReadTask;

    public OracleStreamFetchTask(StreamSplit split) {
        this.split = split;
    }

    @Override
    public void execute(Context context) throws Exception {
        OracleSourceFetchTaskContext sourceFetchContext = (OracleSourceFetchTaskContext) context;
        taskRunning = true;
        redoLogSplitReadTask =
                new OracleRedoLogSplitReadTask(
                        sourceFetchContext.getDbzConnectorConfig(),
                        sourceFetchContext.getOffsetContext(),
                        sourceFetchContext.getConnection(),
                        sourceFetchContext.getDispatcher(),
                        sourceFetchContext.getErrorHandler(),
                        sourceFetchContext.getDatabaseSchema(),
                        sourceFetchContext.getSourceConfig().getOriginDbzConnectorConfig(),
                        sourceFetchContext.getStreamingChangeEventSourceMetrics(),
                        split);
        RedoLogSplitChangeEventSourceContext changeEventSourceContext =
                new RedoLogSplitChangeEventSourceContext();
        redoLogSplitReadTask.execute(
                changeEventSourceContext, sourceFetchContext.getOffsetContext());
    }

    @Override
    public boolean isRunning() {
        return taskRunning;
    }

    @Override
    public StreamSplit getSplit() {
        return split;
    }

    /**
     * A wrapped task to read all binlog for table and also supports read bounded (from lowWatermark
     * to highWatermark) binlog.
     */
    public static class OracleRedoLogSplitReadTask extends LogMinerStreamingChangeEventSource {

        private static final Logger LOG = LoggerFactory.getLogger(OracleRedoLogSplitReadTask.class);
        private final StreamSplit redoLogSplit;
        private final OracleOffsetContext offsetContext;
        private final JdbcSourceEventDispatcher dispatcher;
        private final ErrorHandler errorHandler;
        private ChangeEventSourceContext context;

        public OracleRedoLogSplitReadTask(
                OracleConnectorConfig connectorConfig,
                OracleOffsetContext offsetContext,
                OracleConnection connection,
                JdbcSourceEventDispatcher dispatcher,
                ErrorHandler errorHandler,
                OracleDatabaseSchema schema,
                Configuration jdbcConfig,
                OracleStreamingChangeEventSourceMetrics metrics,
                StreamSplit redoLogSplit) {
            super(
                    connectorConfig,
                    connection,
                    dispatcher,
                    errorHandler,
                    Clock.SYSTEM,
                    schema,
                    jdbcConfig,
                    metrics);
            this.redoLogSplit = redoLogSplit;
            this.dispatcher = dispatcher;
            this.offsetContext = offsetContext;
            this.errorHandler = errorHandler;
        }

        @Override
        public void execute(ChangeEventSourceContext context, OracleOffsetContext offsetContext) {
            this.context = context;
            super.execute(context, offsetContext);
        }

        // TODO: how to stop for fetch binlog for snapshot split? we can reimplements
        // StreamingChangeEventSource that copy from LogMinerStreamingChangeEventSource and add
        // event handle method

        private boolean isBoundedRead() {
            return !NO_STOPPING_OFFSET.equals(redoLogSplit.getEndingOffset());
        }

        public static RedoLogOffset getBinlogPosition(Map<String, ?> offset) {
            Map<String, String> offsetStrMap = new HashMap<>();
            for (Map.Entry<String, ?> entry : offset.entrySet()) {
                offsetStrMap.put(
                        entry.getKey(),
                        entry.getValue() == null ? null : entry.getValue().toString());
            }
            return new RedoLogOffset(offsetStrMap);
        }
    }

    /**
     * The {@link ChangeEventSource.ChangeEventSourceContext} implementation for binlog split task.
     */
    private class RedoLogSplitChangeEventSourceContext
            implements ChangeEventSource.ChangeEventSourceContext {
        @Override
        public boolean isRunning() {
            return taskRunning;
        }
    }
}