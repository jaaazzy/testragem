-- Old links are intentionally retired; pending users can request a new code.
CREATE TABLE email_verification_codes (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    remaining_attempts INTEGER NOT NULL DEFAULT 5 CHECK (remaining_attempts BETWEEN 0 AND 5)
);
DROP TABLE email_verification_tokens;
