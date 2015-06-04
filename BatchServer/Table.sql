create table if not exists tbl_media
(
  media_id integer not null auto_increment,
  media_nm varchar(100) not null,
  media_size integer not null,
  media_path varchar(200) not null,
  primary key (media_id)
);

create table if not exists tbl_user
(
  usr_id varchar(100) not null,
  usr_nm varchar(100) not null,
  passwd varchar(100) not null,
  primary key(usr_id)
);
