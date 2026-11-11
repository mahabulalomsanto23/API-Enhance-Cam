# API Enhance Camera

Android CameraX app that saves the original photo immediately, then enhances a copy in the background through a private backend using the OpenAI Images API.

## Flow
1. Capture a photo.
2. Save the original to the device gallery immediately.
3. Enqueue a persistent WorkManager job.
4. Backend edits the image while preserving the scene and subject.
5. Save the enhanced result as a separate gallery image.

The original is never overwritten. If enhancement fails, the original remains available.

## Project status
Initial project scaffold is being built. Configure the backend API key on the server; never place an OpenAI API key in the Android app.

## Modules
- `app/`: Android CameraX app
- `backend/`: FastAPI service that calls OpenAI Images API
