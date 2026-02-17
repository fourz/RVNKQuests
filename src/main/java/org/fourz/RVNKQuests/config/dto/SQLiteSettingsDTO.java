package org.fourz.RVNKQuests.config.dto;

/**
 * DTO for SQLite connection configuration.
 * Transfers SQLite config from ConfigManager to DatabaseManager.
 */
public class SQLiteSettingsDTO {
    private final String filePath;
    private final String tablePrefix;

    public SQLiteSettingsDTO(String filePath, String tablePrefix) {
        this.filePath = filePath;
        this.tablePrefix = tablePrefix;
    }

    public String getFilePath() { return filePath; }
    public String getTablePrefix() { return tablePrefix != null ? tablePrefix : ""; }
}
