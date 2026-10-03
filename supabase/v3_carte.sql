-- Grace Shop v3 : position GPS de livraison.
alter table public.orders add column if not exists lat double precision;
alter table public.orders add column if not exists lng double precision;
do $$ begin
  alter table public.orders add constraint orders_latlng_chk check ((lat is null or lat between -90 and 90) and (lng is null or lng between -180 and 180));
exception when duplicate_object then null; end $$;
