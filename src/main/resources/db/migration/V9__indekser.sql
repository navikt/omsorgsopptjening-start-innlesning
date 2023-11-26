drop index barnetrygdmottaker_status_id_type;
drop index barnetrygdmottaker_karantene_type;

create index barnetrygdmottaker_status_id_type on barnetrygdmottaker_status(status_type,id ASC);
create index barnetrygdmottaker_karantene_type on barnetrygdmottaker_status(status_type,karantene_til ASC) WHERE (karantene_til is not null);
