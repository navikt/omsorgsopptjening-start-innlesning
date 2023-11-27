alter table barnetrygdmottaker_status add column innlesing_id varchar references innlesing(id) on delete cascade;

create index barnetrygdmottager_status_type_innlesing on barnetrygdmottaker_status(status_type, innlesing_id ASC);
