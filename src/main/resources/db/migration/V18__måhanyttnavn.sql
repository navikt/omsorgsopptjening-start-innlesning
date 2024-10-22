create table barnetrygdinformasjon
(
    id varchar primary key,
    barnetrygdmottaker_id uuid references barnetrygdmottaker(id),
    created timestamptz,
    ident varchar(11),
    persongrunnlag jsonb,
    rådata jsonb,
    correlationId UUID,
    innlesingId UUID,
    status varchar(10),
    lockId UUID,
    lockTime timestamptz
);

create index persongrunnlag_status on barnetrygdinformasjon(status);
create index persongrunnlag_lockid on barnetrygdinformasjon(lockId, lockTime);
create index persongrunnlag_ident on barnetrygdinformasjon(ident)