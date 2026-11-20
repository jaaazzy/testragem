import os
import unittest
from unittest.mock import patch

from fastapi.testclient import TestClient
from testragem_ai.core.settings import Settings
from testragem_ai.main import create_app


class ServiceTests(unittest.TestCase):
    def setUp(self):
        self.client = self.enterContext(TestClient(create_app()))

    def test_health_alias(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"status": "ok", "service": "testragem-ai-service"})

    def test_liveness(self):
        self.assertEqual(self.client.get("/health/live").status_code, 200)

    def test_readiness_requires_real_model(self):
        response = self.client.get("/health/ready")
        self.assertEqual(response.status_code, 503)
        self.assertFalse(response.json()["model_loaded"])
        self.assertEqual(response.json()["reason"], "ai_model_not_implemented")

    def test_no_fake_prediction_endpoint(self):
        self.assertEqual(self.client.post("/predict", json={}).status_code, 404)


class SettingsTests(unittest.TestCase):
    def setUp(self):
        self.enterContext(patch.dict(os.environ, {}, clear=True))

    def test_local_defaults(self):
        self.assertEqual(Settings.from_env(), Settings("127.0.0.1", 8001, "info"))

    def test_environment_overrides(self):
        with patch.dict(os.environ, AI_HOST="0.0.0.0", AI_PORT="8100", AI_LOG_LEVEL="WARNING"):
            self.assertEqual(Settings.from_env(), Settings("0.0.0.0", 8100, "warning"))

    def assert_invalid(self, **values):
        with patch.dict(os.environ, values), self.assertRaises(ValueError):
            Settings.from_env()

    def test_port_below_range(self):
        self.assert_invalid(AI_PORT="0")

    def test_port_above_range(self):
        self.assert_invalid(AI_PORT="65536")

    def test_non_numeric_port(self):
        self.assert_invalid(AI_PORT="abc")

    def test_blank_host(self):
        self.assert_invalid(AI_HOST=" ")

    def test_invalid_log_level(self):
        self.assert_invalid(AI_LOG_LEVEL="invalid")


if __name__ == "__main__":
    unittest.main()
