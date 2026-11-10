-- Existing accounts must also verify ownership; never infer it from registration.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE email_verification_tokens (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
