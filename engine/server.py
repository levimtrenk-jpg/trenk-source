"""
שרת ה-API של המנוע (FastAPI). מאזין רק ל-127.0.0.1.
רק התוסף שה-ID שלו ב-config.json יכול לפנות אליו מהדפדפן; כל Origin אחר נדחה.
ההפעלה הרגילה היא דרך main.pyw (שרת + אייקון במגש).
"""
import logging
import os
import subprocess
from pathlib import Path

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, Response
from pydantic import BaseModel

import config
import downloader
import updater

VERSION = "2.0.0"
log = logging.getLogger("engine.server")

app = FastAPI(title="Universal Downloader Engine", version=VERSION, docs_url=None, redoc_url=None)

def _failed(e: downloader.DownloadFailed):
    return JSONResponse({"detail": str(e), "code": e.code}, status_code=400)


# ---------- אבטחה: Host + Origin ----------

@app.middleware("http")
async def guard(request: Request, call_next):
    host = request.headers.get("host", "")
    # הגנה מ-DNS rebinding: רק כתובת מקומית
    if host.rsplit(":", 1)[0] not in ("127.0.0.1", "localhost"):
        return JSONResponse({"detail": "forbidden host"}, status_code=403)

    origin = request.headers.get("origin")
    if origin is None:
        # בקשה שלא מדף אינטרנט (curl, סקריפט מקומי). דפדפן שולח Origin בכל בקשה cross-origin.
        return await call_next(request)
    if origin not in config.allowed_origins():
        log.warning("rejected origin %s %s %s", origin, request.method, request.url.path)
        return JSONResponse({"detail": "origin not allowed"}, status_code=403)

    cors = {
        "Access-Control-Allow-Origin": origin,
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type",
        "Access-Control-Allow-Private-Network": "true",
        "Vary": "Origin",
    }
    if request.method == "OPTIONS":
        return Response(status_code=204, headers={**cors, "Access-Control-Max-Age": "600"})
    if not request.url.path.startswith("/jobs/"):  # /jobs נקרא כל 700ms, לא ממלאים בו את הלוג
        log.info("extension %s %s", request.method, request.url.path)
    response = await call_next(request)
    response.headers.update(cors)
    return response


# ---------- endpoints ----------

@app.get("/health")
def health():
    return {"ok": True, "version": VERSION, "ytdlp_version": updater.current_version(),
            "download_dir": str(config.download_dir())}


@app.get("/formats")
def formats(url: str):
    try:
        return downloader.probe(url)
    except downloader.DownloadFailed as e:
        return _failed(e)


class DownloadRequest(BaseModel):
    url: str
    format_id: str


@app.post("/download")
def download(req: DownloadRequest):
    try:
        return {"job_id": downloader.start(req.url, req.format_id)}
    except downloader.DownloadFailed as e:
        return _failed(e)


@app.get("/jobs/{job_id}")
def job_status(job_id: str):
    job = downloader.jobs.get(job_id)
    if not job:
        raise HTTPException(404, "משימה לא נמצאה")
    return job


class OpenRequest(BaseModel):
    job_id: str | None = None


@app.post("/open")
def open_folder(req: OpenRequest):
    """פותח את תיקיית ההורדות. עם job_id: פותח את התיקייה ומסמן את הקובץ."""
    folder = config.download_dir().resolve()
    job = downloader.jobs.get(req.job_id) if req.job_id else None
    path = Path(job["file_path"]).resolve() if job and job.get("file_path") else None
    if path and path.is_file() and folder in path.parents:
        subprocess.Popen(["explorer", f"/select,{path}"])
    else:
        os.startfile(folder)
    return {"ok": True}


def active_jobs() -> int:
    return sum(j["status"] in ("queued", "downloading", "processing", "repairing") for j in list(downloader.jobs.values()))


@app.post("/update")
def update():
    """עדכון ידני (לבדיקות). בדרך כלל לא צריך: המנוע מתעדכן לבד בהפעלה, פעם ביום, ובכישלון."""
    if active_jobs():
        raise HTTPException(409, "יש הורדות פעילות. נסה שוב כשהן יסתיימו.")
    result = updater.update("manual")
    if result["changed"]:
        updater.restart_soon()
    return result


class CookiesRequest(BaseModel):
    text: str


@app.post("/cookies")
def cookies(req: CookiesRequest):
    """עוגיות אינסטגרם מכרום (התוסף מייצא אותן אוטומטית כשצריך התחברות), בפורמט Netscape."""
    if len(req.text) > 500_000 or not req.text.startswith("# Netscape HTTP Cookie File"):
        raise HTTPException(400, "קובץ עוגיות לא תקין")
    downloader.AUTO_COOKIES.write_text(req.text, encoding="utf-8")
    log.info("instagram cookies saved (%d lines)", len(req.text.splitlines()))
    return {"ok": True}
