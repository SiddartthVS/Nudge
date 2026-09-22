create or replace function public.get_daily_counts(
    p_device_id text,
    p_from      date default null
)
returns table (day date, counts jsonb)
language plpgsql
security definer
set search_path = public
stable
as $$
begin
    if p_device_id is null or length(p_device_id) = 0 or length(p_device_id) > 64 then
        raise exception 'invalid device id';
    end if;

    return query
    select d.day, d.counts
    from public.daily_scroll_counts d
    where d.device_id = p_device_id
      and (p_from is null or d.day >= p_from)
    order by d.day;
end;
$$;

revoke all on function public.get_daily_counts(text, date) from public;
grant execute on function public.get_daily_counts(text, date) to anon, authenticated;
