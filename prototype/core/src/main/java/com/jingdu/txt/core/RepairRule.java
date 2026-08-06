package com.jingdu.txt.core;

public final class RepairRule {
    public static final int MAXIMUM_FIELD_CHARACTERS = 4096;
    private final String id;
    private final String matchText;
    private final String replacement;
    private final boolean enabled;
    private final int order;
    private final RepairScope scope;
    private final String note;

    public RepairRule(String id, String matchText, String replacement, boolean enabled, int order) {
        this(id, matchText, replacement, enabled, order, RepairScope.CURRENT_BOOK, "");
    }

    public RepairRule(String id, String matchText, String replacement, boolean enabled, int order,
            RepairScope scope, String note) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("rule id must not be blank");
        }
        if (matchText == null || matchText.isEmpty()) {
            throw new IllegalArgumentException("match text must not be empty");
        }
        if (replacement == null) {
            throw new IllegalArgumentException("replacement must not be null");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (note == null) {
            throw new IllegalArgumentException("note must not be null");
        }
        if (id.length() > MAXIMUM_FIELD_CHARACTERS
                || matchText.length() > MAXIMUM_FIELD_CHARACTERS
                || replacement.length() > MAXIMUM_FIELD_CHARACTERS
                || note.length() > MAXIMUM_FIELD_CHARACTERS) {
            throw new IllegalArgumentException("repair rule field exceeds 4096 characters");
        }
        this.id = id;
        this.matchText = matchText;
        this.replacement = replacement;
        this.enabled = enabled;
        this.order = order;
        this.scope = scope;
        this.note = note;
    }

    public String getId() { return id; }
    public String getMatchText() { return matchText; }
    public String getReplacement() { return replacement; }
    public boolean isEnabled() { return enabled; }
    public int getOrder() { return order; }
    public RepairScope getScope() { return scope; }
    public String getNote() { return note; }
}
