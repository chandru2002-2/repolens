package io.repolens.parse.structural;

import java.util.regex.Pattern;

/**
 * Config-file hints (datasource) for deployment / DFD / sequence database roles.
 */
public final class ConfigStructuralFactExtractor {

    private static final Pattern DATASOURCE = Pattern.compile(
            "(?i)(?:jdbc:|datasource|spring\\.datasource\\.url)\\S*");

    private ConfigStructuralFactExtractor() {
    }

    public static void extractDatasourceHint(StructuralFactSink sink, String path, String source) {
        if (!DATASOURCE.matcher(source).find() || sink.factsFull()) {
            return;
        }
        sink.add("deployment", "database", "Datasource", null, null,
                "source=config", path, null);
        sink.add("dfd", "data_store", "Database", null, null,
                "source=config", path, null);
        sink.add("sequence", "database", "Database", null, null,
                "source=config", path, null);
    }
}
