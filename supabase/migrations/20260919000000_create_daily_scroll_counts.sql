-- NUDGE: day-wise scroll counts.
--
-- One row per (device, day). `counts` holds the per-app numbers exactly as the Android
-- service produces them, e.g. {"com.instagram.android": 214, "com.google.android.youtube": 37}.
-- `total` is derived from `counts` so it is easy to chart without unpacking the JSON.

create table if not exists public.daily_scroll_counts (
    device_id  text        not null,
    day        date        not null,
    counts     jsonb       not null default '{}'::jsonb,
    total      integer     not null default 0,
    updated_at timestamptz not null default now(),
    primary key (device_id, day)
);

-- Lock the table completely. With RLS on and NO policies, the public (anon/publishable) key
-- can neither read nor write it directly, so the key baked into the APK cannot be used to
-- download or tamper with anyone's data. The only way in is the function below.
alter table public.daily_scroll_counts enable row level security;

-- The app's single entry point. SECURITY DEFINER = it runs with the owner's rights, which is
-- what lets it write to the locked table on behalf of the anon key. Calling it again for the
-- same (device, day) overwrites that day, so retries are safe (idempotent).
create or replace function public.upsert_daily_counts(
    p_device_id text,
    p_day       date,
    p_counts    jsonb
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if p_device_id is null or length(p_device_id) = 0 or length(p_device_id) > 64 then
        raise exception 'invalid device id';
    end if;

    if p_counts is null or jsonb_typeof(p_counts) <> 'object' then
        raise exception 'counts must be a json object';
    end if;

    insert into public.daily_scroll_counts (device_id, day, counts, total, updated_at)
    values (
        p_device_id,
        p_day,
        p_counts,
        (select coalesce(sum(value::int), 0) from jsonb_each_text(p_counts)),
        now()
    )
    on conflict (device_id, day) do update
        set counts     = excluded.counts,
            total      = excluded.total,
            updated_at = now();
end;
$$;

revoke all on function public.upsert_daily_counts(text, date, jsonb) from public;
grant execute on function public.upsert_daily_counts(text, date, jsonb) to anon, authenticated;
