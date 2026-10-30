CREATE TABLE product_categories (
    code VARCHAR(16) PRIMARY KEY,
    label VARCHAR(20) NOT NULL,
    sort_order INT NOT NULL UNIQUE
);

CREATE TABLE products (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sku VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(180) NOT NULL,
    category VARCHAR(16) NOT NULL,
    brand VARCHAR(60) NOT NULL,
    model VARCHAR(100) NOT NULL,
    summary VARCHAR(240) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    price DECIMAL(12,2) NOT NULL,
    stock INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OFF_SALE',
    source_url VARCHAR(1000) NOT NULL,
    price_note VARCHAR(240) NOT NULL,
    data_version VARCHAR(40) NOT NULL,
    CONSTRAINT fk_product_category FOREIGN KEY (category) REFERENCES product_categories(code),
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_stock CHECK (stock >= 0),
    CONSTRAINT ck_product_status CHECK (status IN ('OFF_SALE', 'ON_SALE'))
);

INSERT INTO product_categories (code, label, sort_order) VALUES
('CPU', 'CPU', 1), ('MOTHERBOARD', '主板', 2), ('GPU', '独显', 3), ('MEMORY', '内存', 4),
('SSD', 'SSD', 5), ('PSU', '电源', 6), ('CASE', '机箱', 7), ('COOLER', '独立散热器', 8);
