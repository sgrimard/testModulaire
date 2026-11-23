create table shipping_product (
    product_id bigint primary key references product (id),
    weight_kg  double precision,
    hazardous  boolean
);
