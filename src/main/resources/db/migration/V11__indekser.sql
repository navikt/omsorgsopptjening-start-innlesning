drop index barnetrygdmottager_status_type_innlesing;
create index barnetrygdmottager_status_type_innlesing_id on barnetrygdmottaker_status(status_type, innlesing_id,id ASC);
create index barnetrygdmottaker_status_type_innlesing_karantene on barnetrygdmottaker_status(status_type,innlesing_id,karantene_til ASC) WHERE (karantene_til is not null);

