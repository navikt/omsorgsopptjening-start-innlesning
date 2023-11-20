create index barnetrygdmottaker_status_type on barnetrygdmottaker_status ((status->>'type'));

alter table barnetrygdmottaker_status drop column kort_status;

