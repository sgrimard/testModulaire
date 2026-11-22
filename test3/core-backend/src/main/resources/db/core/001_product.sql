create table product (
    id          bigserial primary key,
    name        text           not null,
    description text,
    price       numeric(12, 2) not null,
    category    text           not null
);
