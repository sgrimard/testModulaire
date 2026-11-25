create table product (
    id       bigserial primary key,
    name     text           not null,
    price    numeric(12, 2) not null,
    category text           not null,
    stock    integer        not null default 0
);
