package com.dbaas.exception;

/**
 * Exception thrown when a backup is not found.
 */
public class BackupNotFoundException extends RuntimeException {

    public BackupNotFoundException(String backupId) {
        super("Backup not found: " + backupId);
    }

    public BackupNotFoundException(String backupId, Throwable cause) {
        super("Backup not found: " + backupId, cause);
    }
}
