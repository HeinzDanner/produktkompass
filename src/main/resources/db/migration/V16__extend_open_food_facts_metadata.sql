ALTER TABLE product_open_food_facts
    ADD COLUMN brand_tags TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN brand_owner TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN packaging TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN packaging_tags TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN label_tags TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN origin_tags TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN manufacturing_place_tags TEXT;
