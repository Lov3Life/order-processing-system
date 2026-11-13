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
    constraint uq_products_name
        unique (name),
    constraint chk_products_stock_quantity
        check (`stock_quantity` >= 0)
);

create table outbox_events
(
    id              char(36)                      not null
        primary key,
    aggregate_id    bigint                        not null,
    event_type      varchar(100)                  not null,
    topic           varchar(100)                  not null,
    payload         longtext                      not null,
    status          varchar(20) default 'PENDING' not null,
    attempts        int         default 0         not null,
    created_at      datetime(6)                   not null,
    next_attempt_at datetime(6)                   not null,
    published_at    datetime(6)                   null
);

create index idx_outbox_pending
    on outbox_events (status, next_attempt_at, created_at);

create table processed_orders
(
    order_id     bigint       not null
        primary key,
    status       varchar(20)  not null,
    reason       varchar(255) null,
    processed_at datetime(6)  not null
);

