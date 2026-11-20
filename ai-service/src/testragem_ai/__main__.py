import uvicorn
from testragem_ai.core.settings import Settings


# Entry point for python -m testragem_ai. Process variables supply local or container settings.
def main() -> None:
    settings = Settings.from_env()
    uvicorn.run("testragem_ai.main:app", host=settings.host,
                port=settings.port, log_level=settings.log_level)


if __name__ == "__main__":
    main()
