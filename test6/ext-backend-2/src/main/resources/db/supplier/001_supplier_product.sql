create table supplier_product (
    product_id     bigint primary key references product (id),
    supplier       text,
    unit_cost      numeric(12, 2),
    lead_time_days integer
);
