CREATE DATABASE IF NOT EXISTS `order-processing-system-orders_db`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `order-processing-system-orders_db`;

create table customers
(
    customer_id bigint auto_increment
        primary key,
    first_name  varchar(100) not null,
    last_name   varchar(100) not null,
    email       varchar(255) not null,
    constraint uq_customers_email
        unique (email)
);

create table orders
(
    order_id     bigint auto_increment
        primary key,
    customer_id  bigint                                   not null,
    order_status varchar(20) default 'PENDING'            not null,
    order_date   datetime(6) default CURRENT_TIMESTAMP(6) not null,
    constraint fk_orders_customer
        foreign key (customer_id) references customers (customer_id),
    constraint chk_orders_status
        check (`order_status` in ('PENDING','COMPLETED','REJECTED'))
);

create table order_items
(
    order_item_id bigint auto_increment
        primary key,
    order_id      bigint not null,
    product_id    bigint not null,
    quantity      int    not null,
    constraint uq_order_items_order_product
        unique (order_id, product_id),
    constraint fk_order_items_order
        foreign key (order_id) references orders (order_id)
            on delete cascade,
    constraint chk_order_items_quantity
        check (`quantity` > 0)
);

create index idx_order_items_product_id
    on order_items (product_id);

create index idx_orders_customer_id
    on orders (customer_id);

