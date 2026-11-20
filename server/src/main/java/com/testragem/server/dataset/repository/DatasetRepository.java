package com.testragem.server.dataset.repository;

import com.testragem.server.dataset.model.DatasetVersion;
import com.testragem.server.dataset.reader.CsvDatasetReader.Snapshot;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Registers dataset fingerprints. API responses do not expose the operator's file location. */
@Repository
public class DatasetRepository {
    private final JdbcClient jdbc;

    public DatasetRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public DatasetVersion register(Snapshot snapshot, Path path, String source) {
        var candles = snapshot.candles();
        var start = candles.getFirst().timestamp();
        var end = candles.getLast().timestamp();
        String quality = "{\"timezone\":\"UTC\",\"missingBarPolicy\":\"reject-runs-crossing-gaps\","
                + "\"missingHours\":" + snapshot.missingHours() + ",\"candleCount\":" + candles.size() + "}";
        // The fingerprint is unique. Loading identical bytes again reuses the existing dataset identity.
        jdbc.sql("""
                INSERT INTO dataset_versions
                    (id,symbol,timeframe,source,fingerprint,coverage_start,coverage_end,artifact_uri,quality_summary,created_at)
                VALUES (:id,'BTCUSDT','1h',:source,:hash,:start,:end,:uri,CAST(:quality AS JSONB),CURRENT_TIMESTAMP)
                ON CONFLICT (fingerprint) DO NOTHING
                """)
                .param("id", UUID.randomUUID()).param("source", source).param("hash", snapshot.fingerprint())
                .param("start", start.atOffset(ZoneOffset.UTC)).param("end", end.atOffset(ZoneOffset.UTC))
                .param("uri", path.toAbsolutePath().toUri().toString()).param("quality", quality).update();
        return jdbc.sql("""
                SELECT id,symbol,timeframe,source FROM dataset_versions WHERE fingerprint=:hash
                """)
                .param("hash", snapshot.fingerprint())
                .query((rs, row) -> new DatasetVersion(rs.getObject("id", UUID.class),
                        rs.getString("symbol"), rs.getString("timeframe"), rs.getString("source"),
                        snapshot.fingerprint(), start, end, candles.size(), snapshot.missingHours()))
                .single();
    }
}
