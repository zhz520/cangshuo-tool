-- Decision 019: a tool may be hosted on the official site and opened by the app in its WebView
-- container. V1 stays untouched; this migration only relaxes the existing CHECK constraint.
ALTER TABLE tool_definition DROP CHECK ck_tool_definition_mode;
ALTER TABLE tool_definition
    ADD CONSTRAINT ck_tool_definition_mode CHECK (mode IN ('LOCAL', 'SERVER', 'HYBRID', 'WEB'));
