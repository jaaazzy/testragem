"""Read process environment; private .env files are not loaded implicitly."""
from dataclasses import dataclass
import os


@dataclass(frozen=True)
class Settings:
    # Bind to localhost by default because this internal service has no public authentication layer.
    host: str = "127.0.0.1"
    port: int = 8001
    log_level: str = "info"

    # Reject invalid settings before starting the HTTP server.
    def __post_init__(self) -> None:
        if not self.host.strip():
            raise ValueError("AI_HOST must not be blank")
        if not 1 <= self.port <= 65535:
            raise ValueError("AI_PORT must be between 1 and 65535")
        if self.log_level not in {"critical", "error", "warning", "info", "debug", "trace"}:
            raise ValueError("AI_LOG_LEVEL is invalid")

    @classmethod
    def from_env(cls) -> "Settings":
        return cls(
            host=os.environ.get("AI_HOST", "127.0.0.1"),
            port=int(os.environ.get("AI_PORT", "8001")),
            log_level=os.environ.get("AI_LOG_LEVEL", "info").lower(),
        )
