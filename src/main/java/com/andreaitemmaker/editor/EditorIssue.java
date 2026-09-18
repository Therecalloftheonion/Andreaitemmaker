package com.andreaitemmaker.editor;

/**
 * A validation finding for an entry being edited.
 *
 * @param severity ERROR blocks saving, WARNING is reported but savable
 * @param field    the YAML path the problem belongs to ("" for whole-file problems)
 * @param message  a precise, human readable explanation
 */
public record EditorIssue(Severity severity, String field, String message) {

    public enum Severity {
        ERROR,
        WARNING
    }

    public static EditorIssue error(String field, String message) {
        return new EditorIssue(Severity.ERROR, field, message);
    }

    public static EditorIssue warning(String field, String message) {
        return new EditorIssue(Severity.WARNING, field, message);
    }

    public boolean isError() {
        return severity == Severity.ERROR;
    }

    /** {@code field: message}, or just the message for whole-file problems. */
    public String describe() {
        return field == null || field.isEmpty() ? message : field + ": " + message;
    }
}
