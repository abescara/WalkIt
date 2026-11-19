-- Run in the Supabase SQL editor after creating a project.
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique check (username ~ '^[a-z0-9_]{3,24}$'),
  display_name text not null check (length(display_name) between 1 and 60),
  bio text not null default '' check (length(bio) <= 300),
  created_at timestamptz not null default now()
);

create table if not exists public.reports (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles(id) on delete cascade,
  description text not null check (length(trim(description)) > 0),
  category text not null,
  latitude double precision not null check (latitude between 4 and 21.8),
  longitude double precision not null check (longitude between 116 and 127),
  observed_at timestamptz not null default now(),
  created_at timestamptz not null default now()
);

create table if not exists public.comments (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.reports(id) on delete cascade,
  owner_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (length(trim(body)) > 0),
  created_at timestamptz not null default now()
);

create index if not exists reports_owner_idx on public.reports(owner_id);
create index if not exists comments_owner_idx on public.comments(owner_id);
create index if not exists comments_report_idx on public.comments(report_id);

alter table public.profiles enable row level security;
alter table public.reports enable row level security;
alter table public.comments enable row level security;

create policy "Profiles visible to signed in users" on public.profiles for select to authenticated using (true);
create policy "Users create their own profile" on public.profiles for insert to authenticated with check (id = (select auth.uid()));
create policy "Users edit their own profile" on public.profiles for update to authenticated using (id = (select auth.uid())) with check (id = (select auth.uid()));
create policy "Reports visible to signed in users" on public.reports for select to authenticated using (true);
create policy "Users create their own reports" on public.reports for insert to authenticated with check (owner_id = (select auth.uid()));
create policy "Users edit their own reports" on public.reports for update to authenticated using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));
create policy "Users delete their own reports" on public.reports for delete to authenticated using (owner_id = (select auth.uid()));
create policy "Comments visible to signed in users" on public.comments for select to authenticated using (true);
create policy "Users create their own comments" on public.comments for insert to authenticated with check (owner_id = (select auth.uid()));
create policy "Users delete their own comments" on public.comments for delete to authenticated using (owner_id = (select auth.uid()));

create or replace view public.leaderboard with (security_invoker = true) as
select p.id, p.username, p.display_name,
  coalesce(r.report_count, 0) as reports,
  coalesce(c.comment_count, 0) as comments,
  coalesce(r.report_count, 0) * 10 + coalesce(c.comment_count, 0) * 2 as points
from public.profiles p
left join (select owner_id, count(*) as report_count from public.reports group by owner_id) r on r.owner_id = p.id
left join (select owner_id, count(*) as comment_count from public.comments group by owner_id) c on c.owner_id = p.id;

grant select, insert, update on public.profiles to authenticated;
grant select, insert, update, delete on public.reports to authenticated;
grant select, insert, delete on public.comments to authenticated;
grant select on public.leaderboard to authenticated;
