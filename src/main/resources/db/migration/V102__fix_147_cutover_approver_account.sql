-- FIX-147: provision the separately authorized isolated approval-console account.
-- Reuse the enabled local administrator's existing encoded password without
-- displaying it or changing the administrator's role or credentials.
-- Preserve an existing destination account exactly, including disabled state.
-- No cutover state, proposal, approval audit or trading work is changed.
INSERT INTO app_user (username, password, role_name, enabled)
SELECT 'cutover_approver', a.password, 'CUTOVER_APPROVER', TRUE
FROM app_user a
WHERE a.username = 'admin'
  AND a.enabled = TRUE
  AND a.role_name = 'ADMIN'
  AND LENGTH(TRIM(a.password)) > 0
  AND NOT EXISTS (
      SELECT 1 FROM app_user existing_user
      WHERE existing_user.username = 'cutover_approver'
  );
