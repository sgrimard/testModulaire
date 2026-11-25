-- Table propre au plug-in : relation 1-1 avec la table du noyau, qui n'est pas modifiée.
create table shipping_product (
    product_id bigint primary key references product (id) on delete cascade,
    weight_kg  double precision,
    hazardous  boolean
);
