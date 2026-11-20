from fastapi import APIRouter
from fastapi.responses import JSONResponse

router = APIRouter(tags=["health"])


@router.get("/health")
@router.get("/health/live")
# Liveness confirms that the HTTP process runs. It does not confirm that a model is available.
def liveness() -> dict[str, str]:
    return {"status": "ok", "service": "testragem-ai-service"}


@router.get("/health/ready", responses={503: {"description": "No AI model is configured"}})
# Readiness stays unavailable until real model loading and preprocessing exist.
def readiness() -> JSONResponse:
    # Enable readiness only after a real model and versioned preprocessing are loaded.
    return JSONResponse(
        status_code=503,
        content={
            "status": "not_ready",
            "service": "testragem-ai-service",
            "model_loaded": False,
            "reason": "ai_model_not_implemented",
        },
    )
