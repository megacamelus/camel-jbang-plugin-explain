package org.apache.camel.jbang.ai.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.camel.jbang.ai.types.AlpacaRecord;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.io.LocalOutputFile;

public final class ParquetUtil {

    private static final String SCHEMA_FILE = "/training-set-schema.avsc";

    private ParquetUtil() {
        throw new IllegalStateException("Util final class should be instantiated.");
    }

    public static void saveParquet(List<AlpacaRecord> alpacaRecords, Path outputPath) {

        Schema schema;
        try (var schemaContent = ParquetUtil.class.getResourceAsStream(SCHEMA_FILE)) {
            schema = new Schema.Parser().parse(schemaContent);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Parquet schema", e);
        }

        var outputFile = new LocalOutputFile(outputPath);
        var builder = AvroParquetWriter
                .<GenericRecord>builder(outputFile)
                .withSchema(schema);

        try (var writer = builder.build()) {
            for (AlpacaRecord ar : alpacaRecords) {
                GenericRecord r = new GenericData.Record(schema);
                r.put("input", ar.getInput());
                r.put("instruction", ar.getInstruction());
                r.put("output", ar.getOutput());
                writer.write(r);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Parquet file", e);
        }

    }

}
