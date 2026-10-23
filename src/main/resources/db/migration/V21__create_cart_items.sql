CREATE TABLE cart_items (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL,
    PRIMARY KEY (user_id, product_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0)
);
CREATE INDEX idx_cart_items_product ON cart_items(product_id);
