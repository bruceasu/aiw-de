"""Selected frozen images, optional request-owned Files, and metadata-only observation."""
from __future__ import annotations

import base64
import hashlib
import inspect
import os
import time


MAX_BYTES = 512 * 1024
MIMES = {"image/png", "image/jpeg", "image/gif", "image/webp"}


def validate(images):
    if not isinstance(images, (list, tuple)) or len(images) > 4:
        raise RuntimeError("MODEL_IMAGES_LIMIT")
    result, total, seen = [], 0, set()
    for image in images:
        if not isinstance(image, dict) or set(image) != {"id", "mime", "sha256", "base64"}:
            raise RuntimeError("MODEL_IMAGE_INVALID")
        if not isinstance(image["id"], str) or not 1 <= len(image["id"]) <= 128 or image["id"] in seen or image["mime"] not in MIMES:
            raise RuntimeError("MODEL_IMAGE_INVALID")
        seen.add(image["id"])
        if not isinstance(image["base64"], str) or len(image["base64"]) > (MAX_BYTES + 2) // 3 * 4:
            raise RuntimeError("MODEL_IMAGES_LIMIT")
        try:
            raw = base64.b64decode(image["base64"], validate=True)
        except (ValueError, TypeError) as error:
            raise RuntimeError("MODEL_IMAGE_INVALID") from error
        total += len(raw)
        if not raw or total > MAX_BYTES or hashlib.sha256(raw).hexdigest() != image["sha256"]:
            raise RuntimeError("MODEL_IMAGE_INVALID_OR_CHANGED")
        result.append({**image, "data": raw, "url": "data:" + image["mime"] + ";base64," + image["base64"]})
    return result


def input_message(images, text):
    selected = validate(images)
    return [{"role": "user", "content": [{"type": "input_text", "text": text},
        *[{"type": "input_image", "image_url": image["url"], "detail": "auto"} for image in selected]]}]


def observed(items):
    if isinstance(items, list):
        return [observed(item) for item in items]
    if not isinstance(items, dict):
        return items
    if items.get("type") == "input_image":
        url = items.get("image_url", "")
        if isinstance(url, str) and url.startswith("data:image/") and ";base64," in url:
            header, encoded = url.split(";base64,", 1)
            raw = base64.b64decode(encoded, validate=True)
            return {"type": "image_reference", "mime": header[5:], "sha256": hashlib.sha256(raw).hexdigest(),
                    "bytes": len(raw), "representation": "HASHED_REFERENCE"}
    return {key: observed(value) for key, value in items.items()}


def wrap(model, client, request, config):
    images = validate(request.model_images)
    transport = os.environ.get("AIW_MODEL_IMAGE_TRANSPORT", "inline")
    if transport not in {"inline", "files"}:
        raise RuntimeError("MODEL_IMAGE_TRANSPORT_INVALID")
    if not images or transport == "inline":
        return model
    if config.provider == "openai" and config.protocol != "responses":
        raise RuntimeError("MODEL_FILES_PROTOCOL_UNSUPPORTED")
    parameters = inspect.signature(client.files.create).parameters
    if "expires_after" not in parameters:
        raise RuntimeError("MODEL_FILES_SDK_EXPIRY_UNSUPPORTED")
    from agents.models.interface import Model
    selected = {image["url"]: image for image in images}
    owned = {}

    def event(status, **facts):
        request.attempt.evidence.record("model-file", status=status, provider=config.provider, **facts)

    async def project(value):
        if isinstance(value, list):
            return [await project(item) for item in value]
        if not isinstance(value, dict):
            return value
        image = selected.get(value.get("image_url")) if value.get("type") == "input_image" else None
        if image is not None:
            request.attempt.check()
            sha = image["sha256"]
            if sha not in owned:
                event("upload-started", sourceHash=sha)
                try:
                    suffix = {"image/png": "png", "image/jpeg": "jpg", "image/gif": "gif", "image/webp": "webp"}[image["mime"]]
                    result = await client.files.create(file=(sha + "." + suffix, image["data"], image["mime"]), purpose="user_data",
                                                       expires_after={"anchor": "created_at", "seconds": 3600})
                    identity, expiry = result.id, getattr(result, "expires_at", None)
                    if not isinstance(identity, str) or not identity.startswith("file-") or len(identity) > 256:
                        raise RuntimeError("MODEL_FILE_RESPONSE_INVALID")
                    owned[sha] = (identity, expiry)
                    event("created", sourceHash=sha, fileId=identity)
                    if type(expiry) is not int or not time.time() < expiry <= time.time() + 3900:
                        raise RuntimeError("MODEL_FILE_EXPIRY_UNCONFIRMED")
                    event("uploaded", sourceHash=sha, fileId=identity, expiresAt=expiry)
                except BaseException:
                    request.attempt.resource_uncertain = True
                    event("upload-outcome-unknown", sourceHash=sha)
                    raise
            identity, expiry = owned[sha]
            if expiry <= time.time():
                raise RuntimeError("MODEL_FILE_EXPIRED")
            return {"type": "input_image", "file_id": identity, "detail": value.get("detail", "auto")}
        return {key: await project(item) for key, item in value.items()}

    class ImagesModel(Model):
        @property
        def last_usage(self):
            return getattr(model, "last_usage", None)

        async def get_response(self, system_instructions, input, *args, **kwargs):
            return await model.get_response(system_instructions, await project(input), *args, **kwargs)

        def stream_response(self, system_instructions, input, *args, **kwargs):
            async def stream():
                projected = await project(input)
                async for item in model.stream_response(system_instructions, projected, *args, **kwargs):
                    yield item
            return stream()

        async def close(self):
            try:
                for identity, expiry in owned.values():
                    try:
                        result = await client.files.delete(identity, timeout=5)
                        if result.deleted is not True or result.id != identity:
                            raise RuntimeError("MODEL_FILE_DELETE_UNCONFIRMED")
                        event("deleted", fileId=identity)
                    except Exception:
                        # Only IDs created by this request are touched. TTL is the crash/cleanup backstop.
                        event("cleanup-unconfirmed", fileId=identity, expiresAt=expiry)
            finally:
                close = getattr(model, "close", None)
                if close:
                    await close()

    return ImagesModel()
