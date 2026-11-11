import base64
import os
from io import BytesIO

from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.responses import Response
from openai import OpenAI

app = FastAPI(title="API Enhance Camera Backend", version="1.0.0")


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/enhance")
async def enhance(image: UploadFile = File(...)):
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        raise HTTPException(status_code=503, detail="Backend is missing OPENAI_API_KEY")

    content_type = image.content_type or ""
    if content_type not in ("image/jpeg", "image/png", "image/webp"):
        raise HTTPException(status_code=415, detail="Upload a JPEG, PNG, or WebP image")

    raw = await image.read()
    if not raw or len(raw) > 20 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Image is empty or larger than 20 MB")

    suffix = {"image/jpeg": ".jpg", "image/png": ".png", "image/webp": ".webp"}[content_type]
    prompt = (
        "Perform conservative, photorealistic photographic restoration and enhancement of this exact photo. "
        "Preserve the same people, facial identity, expression, age, pose, clothing, objects, background, "
        "camera viewpoint, framing, and scene geometry. Do not add, remove, replace, or redesign anything. "
        "Improve genuine visible clarity: reduce sensor noise and compression artifacts, recover natural fine "
        "texture where supported by the source, refine focus and edge definition gently, balance exposure and "
        "white balance naturally, and retain realistic skin texture. Avoid beauty filters, plastic skin, "
        "oversaturation, HDR halos, invented detail, exaggerated sharpening, or changing facial features. "
        "If detail is not present in the original, do not fabricate it. Return one natural-looking enhanced "
        "version of the same photograph."
    )

    try:
        client = OpenAI(api_key=api_key)
        result = client.images.edit(
            model=os.getenv("OPENAI_IMAGE_MODEL", "gpt-image-1.5"),
            image=(f"original{suffix}", BytesIO(raw), content_type),
            prompt=prompt,
            input_fidelity="high",
            quality="high",
            output_format="jpeg",
        )
        encoded = result.data[0].b64_json
        if not encoded:
            raise RuntimeError("Image API returned no image data")
        output = base64.b64decode(encoded)
        return Response(
            content=output,
            media_type="image/jpeg",
            headers={"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"},
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Image enhancement failed: {str(exc)[:300]}")
