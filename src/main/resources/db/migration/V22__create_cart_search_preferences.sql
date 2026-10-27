CREATE TABLE cart_search_preferences (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    tag_key TEXT NOT NULL,
    tag_state VARCHAR(10) NOT NULL,
    PRIMARY KEY (user_id, tag_key),
    CONSTRAINT ck_cart_search_tag_state CHECK (tag_state IN ('preferred', 'excluded'))
);
