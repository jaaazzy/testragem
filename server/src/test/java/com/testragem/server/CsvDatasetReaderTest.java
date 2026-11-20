package com.testragem.server;
import com.testragem.server.dataset.reader.CsvDatasetReader;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class CsvDatasetReaderTest {
    @TempDir Path dir;
    private Path csv(String rows) throws Exception {Path p=dir.resolve("data.csv");Files.writeString(p,"timestamp_utc,open,high,low,close,volume\n"+rows);return p;}
    @Test void stableHashAndOrderedValues() throws Exception {
        var path=csv("2025-01-01T00:00:00Z,100,110,90,105,20\n2025-01-01T01:00:00Z,105,120,100,115,30\n");
        var reader=new CsvDatasetReader(); assertThat(reader.read(path)).isEqualTo(reader.read(path));
        assertThat(reader.read(path).candles()).hasSize(2);assertThat(reader.read(path).missingHours()).isZero();
    }
    @Test void rejectsDuplicateOutOfOrderAndInvalidOhlc() throws Exception {
        for(String rows:new String[]{"2025-01-01T00:00:00Z,100,110,90,105,20\n2025-01-01T00:00:00Z,100,110,90,105,20", "2025-01-01T00:00:00Z,100,99,90,105,20", "2025-01-01T00:01:00Z,100,110,90,105,20"}) {
            Path path=csv(rows);assertThatThrownBy(()->new CsvDatasetReader().read(path)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void recordsGapsWithoutFillingThem() throws Exception {
        var result=new CsvDatasetReader().read(csv("2025-01-01T00:00:00Z,100,110,90,105,20\n2025-01-01T03:00:00Z,105,120,100,115,30"));
        assertThat(result.candles()).hasSize(2);assertThat(result.missingHours()).isEqualTo(2);
    }
}
