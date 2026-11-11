# API service

This service receives a captured photo and returns an enhanced JPEG. The OpenAI API key stays on the server.

## Run locally

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export OPENAI_API_KEY="your-key"
uvicorn main:app --host 0.0.0.0 --port 8000
```

Health check: `GET /health`

Enhancement: `POST /enhance` with multipart field `image`.

## Deploy
Deploy this folder as a Docker web service. Configure `OPENAI_API_KEY` as a secret environment variable. Set `OPENAI_IMAGE_MODEL` to a supported GPT Image model if desired.

Then update `ENHANCE_API_URL` in `app/build.gradle.kts` to the full HTTPS endpoint ending in `/enhance`, and rebuild the app.

## Important
- A real API key and a deployed HTTPS backend are required before cloud enhancement works.
- Image editing is generative: inspect results for fidelity. The prompt asks the model to preserve identity and scene, but pixel-perfect preservation is not guaranteed.
- Each successful enhancement incurs OpenAI API usage charges.
- Add authentication, rate limiting, upload quotas, and privacy/retention controls before public deployment.
