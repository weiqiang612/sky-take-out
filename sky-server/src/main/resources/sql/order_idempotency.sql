alter table orders
    add column version int not null default 0 comment 'optimistic lock version';

create table if not exists pay_callback_record
(
    id           bigint      not null auto_increment,
    out_trade_no varchar(64) not null,
    callback_body longtext    null,
    create_time   datetime    not null default current_timestamp,
    update_time   datetime    not null default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_pay_callback_record_out_trade_no (out_trade_no)
) engine = innodb
  default charset = utf8mb4
  collate = utf8mb4_general_ci
  comment = 'payment callback idempotency record';
