CREATE DATABASE IF NOT EXISTS `order-processing-system-notifications_db`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `order-processing-system-notifications_db`;

create table notifications
(
    notification_id bigint auto_increment
        primary key,
    order_id        bigint       not null,
    status          varchar(20)  not null,
    message         varchar(500) not null,
    created_at      datetime(6)  not null,
    constraint uq_order_id
        unique (order_id),
    constraint chk_status
        check (`status` in ('ACCEPTED','REJECTED'))
)
charset=utf8mb4;

