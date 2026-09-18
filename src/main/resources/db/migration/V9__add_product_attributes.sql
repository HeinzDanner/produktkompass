ALTER TABLE products ADD COLUMN nutrition_grade VARCHAR(20) DEFAULT 'UNKNOWN';
ALTER TABLE products ADD COLUMN packaging_type VARCHAR(30);
ALTER TABLE products ADD COLUMN package_size VARCHAR(100);
ALTER TABLE products ADD COLUMN made_in_germany BOOLEAN;
ALTER TABLE products ADD COLUMN contains_lactose VARCHAR(20) DEFAULT 'UNKNOWN';
ALTER TABLE products ADD COLUMN contains_gluten VARCHAR(20) DEFAULT 'UNKNOWN';
ALTER TABLE products ADD COLUMN contains_alcohol VARCHAR(20) DEFAULT 'UNKNOWN';
ALTER TABLE products ADD COLUMN contains_caffeine VARCHAR(20) DEFAULT 'UNKNOWN';

CREATE TABLE product_labels
(
    product_id BIGINT      NOT NULL,
    label      VARCHAR(50) NOT NULL,
    CONSTRAINT fk_product_labels_product
        FOREIGN KEY (product_id) REFERENCES products (id)
);
