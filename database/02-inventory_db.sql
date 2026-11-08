CREATE DATABASE IF NOT EXISTS `order-processing-system-inventory_db`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `order-processing-system-inventory_db`;

create table products
(
    product_id     bigint auto_increment
        primary key,
    name           varchar(200)  not null,
    stock_quantity int default 0 not null,
    constraint chk_products_stock_quantity
        check (`stock_quantity` >= 0)
);

