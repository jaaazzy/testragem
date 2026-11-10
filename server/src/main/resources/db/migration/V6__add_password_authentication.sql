-- Existing accounts remain unable to log in until a future verified password
-- enrollment/reset flow supplies a hash. Never assign a shared/default password.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);

-- Fail safely if existing accounts collide under the new policy; reconcile those
-- accounts manually before retrying rather than silently merging identities.
UPDATE users SET email = lower(btrim(email));
ALTER TABLE users ADD CONSTRAINT chk_users_normalized_email
    CHECK (email = lower(btrim(email)) AND email <> '');
