alter table barnetrygdmottaker_status add column status_type varchar(10);
alter table barnetrygdmottaker_status add column karantene_til timestamp with time zone;

create index barnetrygdmottaker_status_id_type on barnetrygdmottaker_status(id,status_type);
create index barnetrygdmottaker_karantene_type on barnetrygdmottaker_status(karantene_til,status_type);
