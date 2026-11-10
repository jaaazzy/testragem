from fastapi import FastAPI

app = FastAPI(title="Testragem Engine", version="0.1.0")


@app.get("/health", tags=["health"])
def health() -> dict[str, str]:
    return {"status": "ok", "service": "testragem-engine"}
