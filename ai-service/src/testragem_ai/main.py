from fastapi import FastAPI
from testragem_ai.api.health import router as health_router


# Build an application per test. Simulation and account ownership remain in Spring Boot.
def create_app() -> FastAPI:
    app = FastAPI(
        title="Testragem AI Service",
        version="0.1.0",
        description="Internal AI research service. Market-regime inference is not implemented yet.",
    )
    app.include_router(health_router)
    return app


app = create_app()
