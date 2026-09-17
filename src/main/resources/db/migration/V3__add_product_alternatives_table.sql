CREATE TABLE product_alternatives
(
    product_id            BIGINT NOT NULL,
    alternative_product_id BIGINT NOT NULL,
    PRIMARY KEY (product_id, alternative_product_id),
    CONSTRAINT fk_product_alternatives_product
        FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_alternatives_alternative_product
        FOREIGN KEY (alternative_product_id) REFERENCES products (id)
);

INSERT INTO product_alternatives (product_id, alternative_product_id)
VALUES ((SELECT id FROM products WHERE name = 'Bio-Hafermilch'),
        (SELECT id FROM products WHERE name = 'Fairtrade Schokolade'));
