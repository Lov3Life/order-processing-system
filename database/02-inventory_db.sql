create table products
(
    product_id     bigint auto_increment
        primary key,
    name           varchar(200)  not null,
    stock_quantity int default 0 not null,
    constraint chk_products_stock_quantity
        check (`stock_quantity` >= 0)
);

