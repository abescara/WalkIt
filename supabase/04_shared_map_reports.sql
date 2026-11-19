-- Run after schema.sql, 02_profile_trigger.sql, and 03_forum.sql.
-- A private Storage bucket keeps report and comment photos available to signed-in users.
insert into storage.buckets (id,name,public,file_size_limit,allowed_mime_types)
values ('report-photos','report-photos',false,10485760,array['image/jpeg'])
on conflict (id) do update set public=false,file_size_limit=10485760,allowed_mime_types=array['image/jpeg'];

create table if not exists public.report_photos (
  id uuid primary key default gen_random_uuid(),
  report_id uuid not null references public.reports(id) on delete cascade,
  owner_id uuid not null references public.profiles(id) on delete cascade,
  position integer not null check (position >= 0),
  storage_path text not null unique,
  created_at timestamptz not null default now(),
  unique (report_id,position)
);
create index if not exists report_photos_report_idx on public.report_photos(report_id,position);
alter table public.report_photos enable row level security;
drop policy if exists "Signed in users read report photos" on public.report_photos;
drop policy if exists "Report owners add photos" on public.report_photos;
create policy "Signed in users read report photos" on public.report_photos
  for select to authenticated using (true);
create policy "Report owners add photos" on public.report_photos
  for insert to authenticated with check (
    owner_id=(select auth.uid()) and
    exists (select 1 from public.reports r where r.id=report_id and r.owner_id=(select auth.uid())) and
    split_part(storage_path,'/',1)=(select auth.uid())::text
  );
grant select,insert on public.report_photos to authenticated;

alter table public.comments add column if not exists anonymous boolean not null default false;
alter table public.comments add column if not exists attachment_path text;
alter table public.comments drop constraint if exists comments_body_check;
alter table public.comments drop constraint if exists comments_body_or_photo_check;
alter table public.comments add constraint comments_body_or_photo_check
  check (char_length(trim(body)) > 0 or attachment_path is not null);

create or replace view public.map_comments with (security_invoker = true) as
select c.id,c.report_id,c.owner_id,c.body,c.anonymous,c.attachment_path,c.created_at,
       p.display_name,p.username
from public.comments c join public.profiles p on p.id=c.owner_id;
grant select on public.map_comments to authenticated;

drop policy if exists "Signed in users read report photo objects" on storage.objects;
drop policy if exists "Users upload their own report photo objects" on storage.objects;
create policy "Signed in users read report photo objects" on storage.objects
  for select to authenticated using (bucket_id='report-photos');
create policy "Users upload their own report photo objects" on storage.objects
  for insert to authenticated with check (
    bucket_id='report-photos' and split_part(name,'/',1)=(select auth.uid())::text
  );

notify pgrst, 'reload schema';
