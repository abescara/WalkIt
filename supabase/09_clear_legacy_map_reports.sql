-- One-time cleanup explicitly requested for all users' older map reports.
-- Cutoff is 2026-11-20 06:14:11 Asia/Manila, when the request was clarified.
-- New reports at or after the cutoff are preserved.
-- Run in Supabase SQL Editor as the project owner. Related comments and report_photos
-- rows cascade with each report. Storage files are not removed by this SQL.
with deleted as (
  delete from public.reports
  where created_at < timestamptz '2026-11-19 22:14:11+00'
  returning id
)
select count(*) as removed_map_reports from deleted;
