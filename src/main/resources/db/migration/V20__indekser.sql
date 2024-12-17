create index barnetrygdinformasjon_status_id_nolock
on barnetrygdinformasjon(status,id ASC)
where lockId is null;