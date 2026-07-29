ALTER TABLE vault_import_session
    ADD COLUMN progress_message VARCHAR(160) NULL AFTER error_code;
