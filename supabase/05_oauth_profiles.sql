-- Run after 02_profile_trigger.sql before enabling Google or Apple sign-in.
-- Social providers do not supply the username chosen by email/password users.
create or replace function public.create_profile_for_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  chosen_name text;
begin
  chosen_name := coalesce(
    nullif(new.raw_user_meta_data ->> 'display_name', ''),
    nullif(new.raw_user_meta_data ->> 'full_name', ''),
    nullif(new.raw_user_meta_data ->> 'name', ''),
    nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
    'Walker'
  );
  insert into public.profiles (id, username, display_name)
  values (
    new.id,
    coalesce(nullif(lower(new.raw_user_meta_data ->> 'username'), ''),
             'walker_' || left(replace(new.id::text, '-', ''), 12)),
    left(chosen_name, 60)
  );
  return new;
end;
$$;
