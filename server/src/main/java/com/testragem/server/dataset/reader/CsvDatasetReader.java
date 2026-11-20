package com.testragem.server.dataset.reader;

import com.testragem.server.dataset.model.Candle;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** A bounded, immutable snapshot. Research/label columns are deliberately ignored. */
@Component
public class CsvDatasetReader {
    public record Snapshot(String fingerprint, List<Candle> candles, long missingHours) {
        public Snapshot { candles=List.copyOf(candles); }
    }
    public Snapshot read(Path path) throws IOException {
        // Read one extra byte to detect oversized input without loading an unbounded file.
        byte[] bytes;
        try (InputStream stream=Files.newInputStream(path)) { bytes=stream.readNBytes(80*1024*1024+1); }
        if (bytes.length>80*1024*1024) throw new IllegalArgumentException("CSV exceeds 80 MB");
        // Hash the exact bytes so saved results can identify their original dataset snapshot.
        String fingerprint;
        try { fingerprint=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        List<Candle> candles=new ArrayList<>(); long missingHours=0;
        try (BufferedReader reader=new BufferedReader(new InputStreamReader(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8))) {
            String header=reader.readLine();
            if (header==null) throw new IllegalArgumentException("CSV is empty");
            List<String> columns=split(header.replace("\ufeff",""));
            if (new HashSet<>(columns).size()!=columns.size()) throw new IllegalArgumentException("Duplicate CSV headers");
            // Read only timestamp and OHLCV fields. Extra research labels must not enter a strategy.
            String time=columns.contains("timestamp_utc") ? "timestamp_utc" : "timestamp";
            for (String key:List.of(time,"open","high","low","close","volume"))
                if (!columns.contains(key)) throw new IllegalArgumentException("Missing CSV column: "+key);
            String line;
            while ((line=reader.readLine())!=null) {
                if (line.isBlank()) continue;
                List<String> values=split(line);
                if (values.size()!=columns.size()) throw new IllegalArgumentException("Invalid CSV row "+(candles.size()+2));
                try {
                    String timestamp=values.get(columns.indexOf(time)).replaceFirst(" ","T");
                    Instant instant=OffsetDateTime.parse(timestamp).toInstant();
                    if (instant.getNano()!=0 || Math.floorMod(instant.getEpochSecond(),3600)!=0) throw new IllegalArgumentException("Candle is not aligned to UTC hour");
                    BigDecimal open=number(values,columns,"open"),high=number(values,columns,"high"),low=number(values,columns,"low"),close=number(values,columns,"close"),volume=number(values,columns,"volume");
                    if (low.signum()<=0 || volume.signum()<0 || high.compareTo(open.max(close))<0 || low.compareTo(open.min(close))>0 || high.compareTo(low)<0)
                        throw new IllegalArgumentException("Invalid OHLCV values");
                    if (!candles.isEmpty()) {
                        long hours=Duration.between(candles.getLast().timestamp(),instant).toHours();
                        if (hours<=0) throw new IllegalArgumentException("Duplicate or out-of-order candle");
                        // Report absent hours without filling the gaps with invented candles.
                        missingHours+=hours-1;
                    }
                    candles.add(new Candle(instant,open,high,low,close,volume));
                    if (candles.size()>100_000) throw new IllegalArgumentException("Dataset exceeds 100,000 candles");
                } catch (RuntimeException ex) { throw new IllegalArgumentException("Invalid CSV row "+(candles.size()+2)+": "+ex.getMessage(),ex); }
            }
        }
        if (candles.isEmpty()) throw new IllegalArgumentException("CSV has no candles");
        return new Snapshot(fingerprint,candles,missingHours);
    }
    private static BigDecimal number(List<String> row,List<String> columns,String name) {
        BigDecimal value=new BigDecimal(row.get(columns.indexOf(name)));
        if (value.scale()>12 || (long)value.precision()-value.scale()>16) throw new IllegalArgumentException("Numeric precision exceeds supported bounds");
        return value;
    }
    private static List<String> split(String line) {
        List<String> fields=new ArrayList<>(); StringBuilder current=new StringBuilder(); boolean quoted=false;
        for (int index=0;index<line.length();index++) {
            char c=line.charAt(index);
            if (c=='"') { if (quoted && index+1<line.length() && line.charAt(index+1)=='"') { current.append('"');index++; } else quoted=!quoted; }
            else if (c==',' && !quoted) { fields.add(current.toString().strip());current.setLength(0); }
            else current.append(c);
        }
        if (quoted) throw new IllegalArgumentException("Unclosed CSV quote");
        fields.add(current.toString().strip());return fields;
    }
}
