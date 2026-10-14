ALTER TABLE product_open_food_facts
    ADD COLUMN category_tags TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN allergen_tags TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN trace_tags TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN countries TEXT;

ALTER TABLE product_open_food_facts
    ADD COLUMN country_tags TEXT;
