alter table barnetrygdmottaker add column lockId UUID;
alter table barnetrygdmottaker add column lockTime timestamptz;

create index barnetrygddmottaker_lockId_lockTime on barnetrygdmottaker(lockId,lockTime);
