alter table barnetrygdmottaker
drop constraint barnetrygdmottaker_innlesing_id_fkey,
add foreign key (innlesing_id) references innlesing(id) on delete cascade;

alter table barnetrygdmottaker_status
drop constraint barnetrygdmottaker_status_id_fkey,
add foreign key (id) references barnetrygdmottaker(id) on delete cascade;