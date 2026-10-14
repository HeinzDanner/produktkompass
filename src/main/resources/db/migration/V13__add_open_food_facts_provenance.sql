CREATE TABLE product_open_food_facts
(
    product_id          BIGINT       NOT NULL PRIMARY KEY,
    barcode             VARCHAR(32)  NOT NULL UNIQUE,
    product_name        VARCHAR(500),
    brands              TEXT,
    categories          TEXT,
    quantity            VARCHAR(255),
    ingredients_text    TEXT,
    allergens           TEXT,
    traces              TEXT,
    nutrition_grade     VARCHAR(20),
    language_code       VARCHAR(35),
    source_modified_at  TIMESTAMP,
    last_imported_at    TIMESTAMP    NOT NULL,
    source_revision     VARCHAR(100),
    CONSTRAINT fk_product_open_food_facts_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
);

CREATE INDEX idx_product_open_food_facts_imported_at
    ON product_open_food_facts (last_imported_at);
