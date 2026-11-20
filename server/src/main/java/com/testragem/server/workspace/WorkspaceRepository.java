package com.testragem.server.workspace;

import static com.testragem.server.workspace.WorkspaceModels.*;
import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.common.error.ApiErrors.Failure;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Every read and mutation is scoped to the session owner in SQL. */
@Repository
public class WorkspaceRepository {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public WorkspaceRepository(JdbcClient jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public List<Preset> presets(UUID owner) {
        return jdbc.sql("""
                SELECT p.id, v.* FROM research_presets p JOIN research_preset_versions v ON v.preset_id=p.id
                WHERE p.owner_id=:owner AND v.version=(SELECT max(x.version) FROM research_preset_versions x WHERE x.preset_id=p.id)
                ORDER BY v.created_at DESC, p.id LIMIT 200
                """).param("owner", owner).query((rs, n) -> preset(rs)).list();
    }

    public List<Preset> versions(UUID owner, UUID id) {
        ownedPreset(owner, id, false);
        return jdbc.sql("SELECT preset_id id, v.* FROM research_preset_versions v WHERE preset_id=:id ORDER BY version DESC")
                .param("id", id).query((rs, n) -> preset(rs)).list();
    }

    @Transactional
    public Preset savePreset(UUID owner, UUID id, String name, BacktestConfiguration configuration) {
        if (id == null) {
            id = UUID.randomUUID();
            jdbc.sql("INSERT INTO research_presets(id,owner_id) VALUES (:id,:owner)")
                    .param("id", id).param("owner", owner).update();
        } else {
            // Serialize revision allocation for a preset. Existing revisions are never overwritten.
            ownedPreset(owner, id, true);
        }
        int version = jdbc.sql("SELECT coalesce(max(version),0)+1 FROM research_preset_versions WHERE preset_id=:id")
                .param("id", id).query(Integer.class).single();
        return jdbc.sql("""
                INSERT INTO research_preset_versions(preset_id,version,name,configuration)
                VALUES (:id,:version,:name,CAST(:config AS JSONB)) RETURNING preset_id id, *
                """).param("id", id).param("version", version).param("name", name)
                .param("config", json.writeValueAsString(configuration)).query((rs, n) -> preset(rs)).single();
    }

    public Preferences preferences(UUID owner) {
        return jdbc.sql("SELECT settings FROM research_preferences WHERE owner_id=:owner").param("owner", owner)
                .query((rs, n) -> json.readValue(rs.getString(1), Preferences.class)).optional().orElse(Preferences.defaults());
    }

    public Preferences savePreferences(UUID owner, Preferences settings) {
        jdbc.sql("""
                INSERT INTO research_preferences(owner_id,settings) VALUES (:owner,CAST(:settings AS JSONB))
                ON CONFLICT (owner_id) DO UPDATE SET settings=EXCLUDED.settings
                """).param("owner", owner).param("settings", json.writeValueAsString(settings)).update();
        return settings;
    }

    public List<RunAnnotation> annotations(UUID owner, List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        return jdbc.sql("""
                SELECT c.id, a.title,a.notes,a.tags FROM backtest_configurations c
                JOIN backtest_results r ON r.config_id=c.id LEFT JOIN research_annotations a ON a.config_id=c.id
                WHERE c.owner_id=:owner AND c.id IN (:ids)
                """).param("owner", owner).param("ids", ids)
                .query((rs, n) -> new RunAnnotation(rs.getObject("id", UUID.class), annotation(rs))).list();
    }

    public Annotation saveAnnotation(UUID owner, UUID id, Annotation annotation) {
        int written = jdbc.sql("""
                INSERT INTO research_annotations(config_id,title,notes,tags)
                SELECT c.id,:title,:notes,CAST(:tags AS JSONB) FROM backtest_configurations c
                JOIN backtest_results r ON r.config_id=c.id WHERE c.id=:id AND c.owner_id=:owner
                ON CONFLICT (config_id) DO UPDATE SET title=EXCLUDED.title,notes=EXCLUDED.notes,tags=EXCLUDED.tags
                """).param("id", id).param("owner", owner).param("title", annotation.title())
                .param("notes", annotation.notes()).param("tags", json.writeValueAsString(annotation.tags())).update();
        if (written == 0) throw new Failure(HttpStatus.NOT_FOUND, "backtest_not_found");
        return annotation;
    }

    private void ownedPreset(UUID owner, UUID id, boolean lock) {
        jdbc.sql("SELECT id FROM research_presets WHERE id=:id AND owner_id=:owner" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).param("owner", owner).query(UUID.class).optional()
                .orElseThrow(() -> new Failure(HttpStatus.NOT_FOUND, "preset_not_found"));
    }

    private Preset preset(ResultSet rs) throws SQLException {
        return new Preset(rs.getObject("id", UUID.class), rs.getInt("version"), rs.getString("name"),
                json.readValue(rs.getString("configuration"), BacktestConfiguration.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private Annotation annotation(ResultSet rs) throws SQLException {
        if (rs.getString("title") == null) return Annotation.empty();
        return new Annotation(rs.getString("title"), rs.getString("notes"),
                Arrays.asList(json.readValue(rs.getString("tags"), String[].class)));
    }
}
