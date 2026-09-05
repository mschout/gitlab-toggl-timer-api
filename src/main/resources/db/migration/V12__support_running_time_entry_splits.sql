ALTER TABLE time_entry_split_operations
    ADD COLUMN operation_kind VARCHAR(32) NOT NULL DEFAULT 'COMPLETED';

ALTER TABLE time_entry_split_operations
    ALTER COLUMN original_stop DROP NOT NULL;

ALTER TABLE time_entry_split_operations
    DROP CONSTRAINT chk_time_entry_split_interval;

ALTER TABLE time_entry_split_operations
    ADD CONSTRAINT chk_time_entry_split_interval CHECK (
        (operation_kind = 'COMPLETED'
            AND original_stop IS NOT NULL
            AND original_start < split_at
            AND split_at < original_stop)
        OR
        (operation_kind = 'RUNNING'
            AND original_stop IS NULL
            AND original_start < split_at)
    );
