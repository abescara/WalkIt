-- Friend requests and accepted connections. Run after schema.sql.
create table if not exists public.friendships (
  id uuid primary key default gen_random_uuid(),
  requester_id uuid not null references public.profiles(id) on delete cascade,
  addressee_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (requester_id <> addressee_id)
);
create unique index if not exists friendships_pair_unique on public.friendships
  (least(requester_id,addressee_id),greatest(requester_id,addressee_id));
create index if not exists friendships_addressee_idx on public.friendships(addressee_id,status);
alter table public.friendships enable row level security;
drop policy if exists "Participants read friendships" on public.friendships;
drop policy if exists "Users send friend requests" on public.friendships;
drop policy if exists "Recipients accept friend requests" on public.friendships;
drop policy if exists "Participants remove friendships" on public.friendships;
create policy "Participants read friendships" on public.friendships for select to authenticated
  using (requester_id=(select auth.uid()) or addressee_id=(select auth.uid()));
create policy "Users send friend requests" on public.friendships for insert to authenticated
  with check (requester_id=(select auth.uid()) and status='pending');
create policy "Recipients accept friend requests" on public.friendships for update to authenticated
  using (addressee_id=(select auth.uid()) and status='pending')
  with check (addressee_id=(select auth.uid()) and status='accepted');
create policy "Participants remove friendships" on public.friendships for delete to authenticated
  using (requester_id=(select auth.uid()) or addressee_id=(select auth.uid()));
revoke update on public.friendships from authenticated;
grant select,insert,delete on public.friendships to authenticated;
grant update(status) on public.friendships to authenticated;
notify pgrst, 'reload schema';
