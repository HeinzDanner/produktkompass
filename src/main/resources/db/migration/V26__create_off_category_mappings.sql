CREATE TABLE off_category_mappings (
    off_value TEXT PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE
);
CREATE INDEX idx_off_category_mappings_category ON off_category_mappings(category_id);
