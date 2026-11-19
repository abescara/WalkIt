-- Run after 03_forum.sql. Adds post photos and one vote per user per post.
alter table public.forum_posts add column if not exists image_path text;
create table if not exists public.forum_votes (
  post_id bigint not null references public.forum_posts(id) on delete cascade,
  voter_id uuid not null references public.profiles(id) on delete cascade,
  value smallint not null check (value in (-1,1)),
  primary key (post_id,voter_id)
);
alter table public.forum_votes enable row level security;
drop policy if exists "Read forum votes" on public.forum_votes;
drop policy if exists "Add own forum vote" on public.forum_votes;
drop policy if exists "Change own forum vote" on public.forum_votes;
drop policy if exists "Remove own forum vote" on public.forum_votes;
create policy "Read forum votes" on public.forum_votes for select to authenticated using (true);
create policy "Add own forum vote" on public.forum_votes for insert to authenticated with check (voter_id=(select auth.uid()));
create policy "Change own forum vote" on public.forum_votes for update to authenticated using (voter_id=(select auth.uid())) with check (voter_id=(select auth.uid()));
create policy "Remove own forum vote" on public.forum_votes for delete to authenticated using (voter_id=(select auth.uid()));
grant select,insert,delete on public.forum_votes to authenticated;
grant update(value) on public.forum_votes to authenticated;

create or replace view public.forum_feed with (security_invoker = true) as
select p.id, p.author_id, a.username, a.display_name, p.title, p.body, p.created_at,
  (select count(*) from public.forum_replies r where r.post_id = p.id) as reply_count,
  p.image_path,
  (select coalesce(sum(v.value),0) from public.forum_votes v where v.post_id=p.id) as score,
  (select v.value from public.forum_votes v where v.post_id=p.id and v.voter_id=(select auth.uid())) as my_vote
from public.forum_posts p join public.profiles a on a.id = p.author_id;
grant select on public.forum_feed to authenticated;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('forum-photos','forum-photos',false,5242880,array['image/jpeg'])
on conflict(id) do update set public=false,file_size_limit=5242880,allowed_mime_types=array['image/jpeg'];
drop policy if exists "Read forum photos" on storage.objects;
drop policy if exists "Upload own forum photos" on storage.objects;
create policy "Read forum photos" on storage.objects for select to authenticated
  using (bucket_id='forum-photos');
create policy "Upload own forum photos" on storage.objects for insert to authenticated
  with check (bucket_id='forum-photos' and split_part(name,'/',1)=(select auth.uid())::text);
notify pgrst, 'reload schema';
