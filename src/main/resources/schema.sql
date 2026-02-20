create table if not exists config
(
    id          int auto_increment
        primary key,
    config_key  varchar(255)                         not null,
    value       varchar(255)                         not null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    is_active   tinyint(1) default 1                 not null
);

create table if not exists court
(
    id          int auto_increment
        primary key,
    court_name  varchar(255)                         null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_active   tinyint(1) default 1                 not null
);

create table if not exists flash_session
(
    id            int auto_increment
        primary key,
    flash_time    time                                 not null,
    begin_time    time                                 not null,
    end_time      time                                 not null,
    slot_interval int                                  not null,
    create_time   timestamp  default CURRENT_TIMESTAMP not null,
    update_time   timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_active     tinyint(1) default 1                 not null
);

create table if not exists time_slot
(
    id          int auto_increment
        primary key,
    slot_date   date                                 not null,
    start_time  time                                 not null,
    end_time    time                                 not null,
    court_id    int                                  not null,
    session_id  int                                  not null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_active   tinyint(1) default 1                 not null,
    constraint time_slot_pk_2
        unique (slot_date, start_time, end_time, court_id, session_id),
    constraint time_slot_court_id_fk
        foreign key (court_id) references court (id),
    constraint time_slot_session_id_fk
        foreign key (session_id) references flash_session (id)
);

create table if not exists user_account
(
    id          int auto_increment
        primary key,
    student_id  varchar(31)                          not null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_active   tinyint(1) default 1                 not null,
    constraint user_account_pk
        unique (student_id)
);

create table if not exists reservation
(
    id          int auto_increment
        primary key,
    user_id     int                                  not null,
    slot_id     int                                  not null,
    status      varchar(127)                         not null,
    create_time timestamp  default CURRENT_TIMESTAMP not null,
    update_time timestamp  default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    is_active   tinyint(1) default 1                 not null,
    constraint reservation_pk
        unique (slot_id),
    constraint reservation___fk
        foreign key (slot_id) references time_slot (id),
    constraint reservation_user_id_fk
        foreign key (user_id) references user_account (id)
);
