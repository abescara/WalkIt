-- Run once in Supabase SQL Editor after schema.sql and 02_profile_trigger.sql.
create table if not exists public.forum_posts (
  id bigint generated always as identity primary key,
  author_id uuid not null references public.profiles(id) on delete cascade,
  title text not null check (char_length(trim(title)) between 5 and 100),
  body text not null check (char_length(trim(body)) between 10 and 3000),
  created_at timestamptz not null default now()
);

create table if not exists public.forum_replies (
  id bigint generated always as identity primary key,
  post_id bigint not null references public.forum_posts(id) on delete cascade,
  author_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(trim(body)) between 1 and 1000),
  created_at timestamptz not null default now()
);

create index if not exists forum_posts_created_at_idx on public.forum_posts(created_at desc);
create index if not exists forum_replies_post_id_created_at_idx on public.forum_replies(post_id,created_at);

alter table public.forum_posts enable row level security;
alter table public.forum_replies enable row level security;

drop policy if exists "Signed in users read forum posts" on public.forum_posts;
drop policy if exists "Users create their own forum posts" on public.forum_posts;
drop policy if exists "Users delete their own forum posts" on public.forum_posts;
drop policy if exists "Signed in users read forum replies" on public.forum_replies;
drop policy if exists "Users create their own forum replies" on public.forum_replies;
drop policy if exists "Users delete their own forum replies" on public.forum_replies;

create policy "Signed in users read forum posts" on public.forum_posts
  for select to authenticated using (true);
create policy "Users create their own forum posts" on public.forum_posts
  for insert to authenticated with check (author_id = (select auth.uid()));
create policy "Users delete their own forum posts" on public.forum_posts
  for delete to authenticated using (author_id = (select auth.uid()));
create policy "Signed in users read forum replies" on public.forum_replies
  for select to authenticated using (true);
create policy "Users create their own forum replies" on public.forum_replies
  for insert to authenticated with check (author_id = (select auth.uid()));
create policy "Users delete their own forum replies" on public.forum_replies
  for delete to authenticated using (author_id = (select auth.uid()));

create or replace view public.forum_feed with (security_invoker = true) as
select p.id, p.author_id, a.username, a.display_name, p.title, p.body, p.created_at,
  (select count(*) from public.forum_replies r where r.post_id = p.id) as reply_count
from public.forum_posts p join public.profiles a on a.id = p.author_id;

create or replace view public.forum_thread_replies with (security_invoker = true) as
select r.id, r.post_id, r.author_id, a.username, a.display_name, r.body, r.created_at
from public.forum_replies r join public.profiles a on a.id = r.author_id;

grant select, insert, delete on public.forum_posts to authenticated;
grant select, insert, delete on public.forum_replies to authenticated;
grant usage, select on sequence public.forum_posts_id_seq to authenticated;
grant usage, select on sequence public.forum_replies_id_seq to authenticated;
grant select on public.forum_feed to authenticated;
grant select on public.forum_thread_replies to authenticated;
notify pgrst, 'reload schema';
