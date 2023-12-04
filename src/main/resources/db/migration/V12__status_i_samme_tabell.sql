drop table barnetrygdmottaker_status;

alter table barnetrygdmottaker add column status jsonb not null;
alter table barnetrygdmottaker add column statushistorikk jsonb not null;
alter table barnetrygdmottaker add column status_type varchar(10) not null;
alter table barnetrygdmottaker add column karantene_til timestamp with time zone;

create index barnetrygdmottaker_innlesing_statustype_id on barnetrygdmottaker(innlesing_id,status_type,id);
create index barnetrygdmottaker_innlesing_statustype_karantene on barnetrygdmottaker(innlesing_id,status_type,karantene_til) where karantene_til is not null;