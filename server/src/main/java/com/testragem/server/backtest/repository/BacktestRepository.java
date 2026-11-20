package com.testragem.server.backtest.repository;

import com.testragem.server.backtest.model.BacktestConfiguration;
import com.testragem.server.backtest.model.TradeFill;
import com.testragem.server.backtest.model.EquityPoint;
import com.testragem.server.backtest.model.BacktestOutcome;
import com.testragem.server.backtest.model.IndicatorPoint;
import com.testragem.server.backtest.dto.BacktestResponse;
import com.testragem.server.backtest.dto.BacktestSummary;
import com.testragem.server.backtest.dto.BacktestHistory;

import com.testragem.server.common.error.ApiErrors.Failure;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Stores completed runs and reads results that belong to the authenticated owner. */
@Repository
public class BacktestRepository {
    private final JdbcClient jdbc; private final ObjectMapper json; private final JdbcTemplate batch;
    public BacktestRepository(JdbcClient jdbc,ObjectMapper json,JdbcTemplate batch) { this.jdbc=jdbc;this.json=json;this.batch=batch; }
    // Configuration, summary, observations, fills, and equity must commit together. Any insert failure rolls back this method.
    @Transactional
    public BacktestResponse save(UUID owner,BacktestConfiguration config,String fingerprint,BacktestOutcome result) {
        // One ID connects the immutable configuration to its accepted result and detail rows.
        UUID id=UUID.randomUUID(); Instant completed=Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.sql("INSERT INTO backtest_configurations(id,owner_id,dataset_version_id,name,range_start,range_end,initial_capital,fee_bps,slippage_bps,parameters,execution_policy) VALUES (:id,:owner,:dataset,:name,:start,:end,:capital,:fee,:slip,CAST(:params AS JSONB),CAST(:policy AS JSONB))")
            .param("id",id).param("owner",owner).param("dataset",config.datasetId()).param("name",config.strategy())
            .param("start",config.rangeStart().atOffset(ZoneOffset.UTC)).param("end",config.rangeEnd().atOffset(ZoneOffset.UTC)).param("capital",config.initialCapital())
            .param("fee",config.feeBps()).param("slip",config.slippageBps()).param("params",json.writeValueAsString(config.parameters()))
            .param("policy",json.writeValueAsString(Map.of("allocationPercent",config.allocationPercent(),"strategy",config.strategy(),"strategyVersion",config.strategyVersion(),"entryTiming","next-bar-open","endPolicy","mark-to-market"))).update();
        jdbc.sql("INSERT INTO backtest_results(config_id,final_equity,total_return,max_drawdown,total_fees,total_slippage,engine_version,status,completed_at,configuration_snapshot,dataset_fingerprint,indicator_points) VALUES (:id,:equity,:ret,:dd,:fees,:slippage,:version,'SUCCEEDED',:time,CAST(:config AS JSONB),:hash,CAST(:indicators AS JSONB))")
            .param("id",id).param("equity",result.finalEquity()).param("ret",result.totalReturn()).param("dd",result.maxDrawdown()).param("fees",result.totalFees()).param("slippage",result.totalSlippage())
            .param("version",result.engineVersion()).param("time",completed.atOffset(ZoneOffset.UTC)).param("config",json.writeValueAsString(config)).param("hash",fingerprint).param("indicators",json.writeValueAsString(result.indicatorPoints())).update();
        // Write detail rows in batches of 500 to reduce database calls for large runs.
        batch.batchUpdate("INSERT INTO trade_fills(config_id,sequence_number,executed_at,side,quantity,reference_price,execution_price,fee,slippage_cost,cash_after) VALUES (?,?,?,?,?,?,?,?,?,?)",result.fills(),500,(ps,fill)->{
            ps.setObject(1,id);ps.setInt(2,fill.sequenceNumber());ps.setObject(3,fill.executedAt().atOffset(ZoneOffset.UTC));ps.setString(4,fill.side());ps.setBigDecimal(5,fill.quantity());ps.setBigDecimal(6,fill.referencePrice());ps.setBigDecimal(7,fill.executionPrice());ps.setBigDecimal(8,fill.fee());ps.setBigDecimal(9,fill.slippageCost());ps.setBigDecimal(10,fill.cashAfter());
        });
        batch.batchUpdate("INSERT INTO equity_points(config_id,timestamp,cash,position_quantity,equity,drawdown) VALUES (?,?,?,?,?,?)",result.equityPoints(),500,(ps,point)->{
            ps.setObject(1,id);ps.setObject(2,point.timestamp().atOffset(ZoneOffset.UTC));ps.setBigDecimal(3,point.cash());ps.setBigDecimal(4,point.positionQuantity());ps.setBigDecimal(5,point.equity());ps.setBigDecimal(6,point.drawdown());
        });
        return new BacktestResponse(id,config,fingerprint,completed,result);
    }
    // Apply ownership in SQL. The ID tie-breaker gives equal completion times a stable order.
    public BacktestHistory history(UUID owner,int page,int size) {
        if (page<0 || page>10000 || size<1 || size>50) throw new Failure(HttpStatus.BAD_REQUEST,"invalid_pagination");
        long total=jdbc.sql("SELECT count(*) FROM backtest_results r JOIN backtest_configurations c ON c.id=r.config_id WHERE c.owner_id=:owner").param("owner",owner).query(Long.class).single();
        List<BacktestSummary> rows=jdbc.sql("SELECT c.id,c.name strategy,c.initial_capital,r.final_equity,r.total_return,r.completed_at FROM backtest_results r JOIN backtest_configurations c ON c.id=r.config_id WHERE c.owner_id=:owner ORDER BY r.completed_at DESC,c.id DESC LIMIT :size OFFSET :offset")
            .param("owner",owner).param("size",size).param("offset",page*size).query((rs,n)->new BacktestSummary(rs.getObject("id",UUID.class),rs.getString("strategy"),rs.getBigDecimal("initial_capital"),rs.getBigDecimal("final_equity"),rs.getBigDecimal("total_return"),rs.getObject("completed_at",OffsetDateTime.class).toInstant())).list();
        return new BacktestHistory(rows,page,size,total);
    }
    // Check ownership before reading detail rows. Unknown and other users' IDs both return 404.
    public BacktestResponse get(UUID owner,UUID id) {
        return jdbc.sql("SELECT r.* FROM backtest_results r JOIN backtest_configurations c ON c.id=r.config_id WHERE c.owner_id=:owner AND r.config_id=:id")
            .param("owner",owner).param("id",id).query((rs,n)->{
                BacktestConfiguration config=json.readValue(rs.getString("configuration_snapshot"),BacktestConfiguration.class);
                List<TradeFill> fills=jdbc.sql("SELECT * FROM trade_fills WHERE config_id=:id ORDER BY sequence_number").param("id",id)
                    .query((f,i)->new TradeFill(f.getInt("sequence_number"),f.getObject("executed_at",OffsetDateTime.class).toInstant(),f.getString("side"),f.getBigDecimal("quantity"),f.getBigDecimal("reference_price"),f.getBigDecimal("execution_price"),f.getBigDecimal("fee"),f.getBigDecimal("slippage_cost"),f.getBigDecimal("cash_after"))).list();
                List<EquityPoint> points=jdbc.sql("SELECT * FROM equity_points WHERE config_id=:id ORDER BY timestamp").param("id",id)
                    .query((e,i)->new EquityPoint(e.getObject("timestamp",OffsetDateTime.class).toInstant(),e.getBigDecimal("cash"),e.getBigDecimal("position_quantity"),e.getBigDecimal("equity"),e.getBigDecimal("drawdown"))).list();
                BacktestOutcome result=new BacktestOutcome(rs.getBigDecimal("final_equity"),rs.getBigDecimal("total_return"),rs.getBigDecimal("max_drawdown"),rs.getBigDecimal("total_fees"),rs.getBigDecimal("total_slippage"),rs.getString("engine_version"),fills,points,Arrays.asList(json.readValue(rs.getString("indicator_points"),IndicatorPoint[].class)));
                return new BacktestResponse(id,config,rs.getString("dataset_fingerprint"),rs.getObject("completed_at",OffsetDateTime.class).toInstant(),result);
            }).optional().orElseThrow(()->new Failure(HttpStatus.NOT_FOUND,"backtest_not_found"));
    }
}
