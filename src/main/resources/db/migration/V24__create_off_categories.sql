CREATE TABLE product_off_categories (
    product_id BIGINT NOT NULL REFERENCES product_open_food_facts(product_id) ON DELETE CASCADE,
    category_value TEXT NOT NULL,
    PRIMARY KEY (product_id, category_value)
);

CREATE INDEX idx_product_off_categories_value ON product_off_categories(category_value);
