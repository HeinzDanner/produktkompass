CREATE TABLE app_user_favorites (
    user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, product_id),
    CONSTRAINT fk_app_user_favorites_user
        FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_app_user_favorites_product
        FOREIGN KEY (product_id) REFERENCES products (id)
);
