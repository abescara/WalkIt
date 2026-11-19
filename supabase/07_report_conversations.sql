-- Run after 04_shared_map_reports.sql. Adds threaded discussion, reactions, and shared profile pictures.
alter table public.profiles add column if not exists avatar_path text;
alter table public.comments add column if not exists parent_id uuid references public.comments(id) on delete cascade;
create index if not exists comments_parent_idx on public.comments(parent_id,created_at);

create or replace function public.validate_report_reply()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.parent_id is not null and not exists (
    select 1 from public.comments parent
    where parent.id=new.parent_id and parent.report_id=new.report_id
  ) then raise exception 'Reply must belong to the same report'; end if;
  return new;
end;
$$;
drop trigger if exists validate_report_reply on public.comments;
create trigger validate_report_reply before insert or update of parent_id,report_id on public.comments
for each row execute function public.validate_report_reply();

create or replace view public.map_comments with (security_invoker = true) as
select c.id,c.report_id,c.owner_id,c.body,c.anonymous,c.attachment_path,c.created_at,
       p.display_name,p.username,c.parent_id,p.avatar_path
from public.comments c join public.profiles p on p.id=c.owner_id;
grant select on public.map_comments to authenticated;

create table if not exists public.report_reactions (
  report_id uuid not null references public.reports(id) on delete cascade,
  owner_id uuid not null references public.profiles(id) on delete cascade,
  kind text not null check (kind in ('like','love','care','haha','wow','sad','angry')),
  created_at timestamptz not null default now(),
  primary key(report_id,owner_id)
);
create table if not exists public.comment_reactions (
  comment_id uuid not null references public.comments(id) on delete cascade,
  owner_id uuid not null references public.profiles(id) on delete cascade,
  kind text not null check (kind in ('like','love','care','haha','wow','sad','angry')),
  created_at timestamptz not null default now(),
  primary key(comment_id,owner_id)
);
alter table public.report_reactions enable row level security;
alter table public.comment_reactions enable row level security;
drop policy if exists "Read report reactions" on public.report_reactions;
drop policy if exists "Add own report reaction" on public.report_reactions;
drop policy if exists "Change own report reaction" on public.report_reactions;
drop policy if exists "Remove own report reaction" on public.report_reactions;
create policy "Read report reactions" on public.report_reactions for select to authenticated using (true);
create policy "Add own report reaction" on public.report_reactions for insert to authenticated with check (owner_id=(select auth.uid()));
create policy "Change own report reaction" on public.report_reactions for update to authenticated using (owner_id=(select auth.uid())) with check (owner_id=(select auth.uid()));
create policy "Remove own report reaction" on public.report_reactions for delete to authenticated using (owner_id=(select auth.uid()));
drop policy if exists "Read comment reactions" on public.comment_reactions;
drop policy if exists "Add own comment reaction" on public.comment_reactions;
drop policy if exists "Change own comment reaction" on public.comment_reactions;
drop policy if exists "Remove own comment reaction" on public.comment_reactions;
create policy "Read comment reactions" on public.comment_reactions for select to authenticated using (true);
create policy "Add own comment reaction" on public.comment_reactions for insert to authenticated with check (owner_id=(select auth.uid()));
create policy "Change own comment reaction" on public.comment_reactions for update to authenticated using (owner_id=(select auth.uid())) with check (owner_id=(select auth.uid()));
create policy "Remove own comment reaction" on public.comment_reactions for delete to authenticated using (owner_id=(select auth.uid()));
grant select,insert,delete on public.report_reactions,public.comment_reactions to authenticated;
grant update(kind) on public.report_reactions,public.comment_reactions to authenticated;
create or replace view public.map_comment_reactions with (security_invoker = true) as
select r.comment_id,r.owner_id,r.kind,c.report_id
from public.comment_reactions r join public.comments c on c.id=r.comment_id;
grant select on public.map_comment_reactions to authenticated;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
values ('profile-photos','profile-photos',false,5242880,array['image/jpeg'])
on conflict(id) do update set public=false,file_size_limit=5242880,allowed_mime_types=array['image/jpeg'];
drop policy if exists "Read profile photos" on storage.objects;
drop policy if exists "Upload own profile photos" on storage.objects;
create policy "Read profile photos" on storage.objects for select to authenticated
  using (bucket_id='profile-photos');
create policy "Upload own profile photos" on storage.objects for insert to authenticated
  with check (bucket_id='profile-photos' and split_part(name,'/',1)=(select auth.uid())::text);
notify pgrst, 'reload schema';
