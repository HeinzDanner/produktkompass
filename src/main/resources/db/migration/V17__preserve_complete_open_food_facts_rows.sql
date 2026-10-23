ALTER TABLE product_open_food_facts ADD COLUMN source_row_json TEXT;
ALTER TABLE product_open_food_facts ADD COLUMN merged_source_json TEXT;
ALTER TABLE product_open_food_facts ADD COLUMN metadata_json TEXT;
ALTER TABLE product_open_food_facts ADD COLUMN serving_size TEXT;
ALTER TABLE product_open_food_facts ADD COLUMN nova_group INTEGER;
ALTER TABLE product_open_food_facts ADD COLUMN nutriscore_score NUMERIC(12, 4);
ALTER TABLE product_open_food_facts ADD COLUMN completeness NUMERIC(12, 4);
ALTER TABLE product_open_food_facts ADD COLUMN source_created_at TIMESTAMP;
