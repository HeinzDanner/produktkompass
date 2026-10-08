CREATE INDEX idx_products_name_id ON products (name, id);
CREATE INDEX idx_products_brand_id ON products (brand_id);
CREATE INDEX idx_products_company_id ON products (company_id);
CREATE INDEX idx_products_nutrition_grade ON products (nutrition_grade);
CREATE INDEX idx_product_categories_category_product ON product_categories (category_id, product_id);
CREATE INDEX idx_product_labels_label_product ON product_labels (label, product_id);
