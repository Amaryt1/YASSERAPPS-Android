"""Shared star ratings only, no comments or personal accounts.
Run behind an HTTPS reverse proxy using uvicorn. Random install ID is pseudonymous.
"""
import os, sqlite3, re, hashlib, hmac, time, threading
from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel, Field, UUID4
from starlette.responses import JSONResponse

DB=os.environ.get("RATINGS_DB","ratings.sqlite3")
SECRET=os.environ.get("RATINGS_SECRET","")
if len(SECRET)<32: raise RuntimeError("Set RATINGS_SECRET to a strong random secret")
app=FastAPI(title="YASSERAPPS ratings",docs_url=None,redoc_url=None,openapi_url=None)
def connect():
    db=sqlite3.connect(DB,timeout=5)
    db.execute("PRAGMA busy_timeout=5000")
    return db
with connect() as db:
    db.execute("PRAGMA journal_mode=WAL")
    db.execute("CREATE TABLE IF NOT EXISTS votes(app TEXT NOT NULL,voter TEXT NOT NULL,score INTEGER NOT NULL CHECK(score BETWEEN 1 AND 5),PRIMARY KEY(app,voter))")
    db.execute("CREATE TABLE IF NOT EXISTS limits(key TEXT NOT NULL,window INTEGER NOT NULL,n INTEGER NOT NULL,PRIMARY KEY(key,window))")
def valid(app_id):
    if not re.fullmatch(r"[A-Za-z0-9._:/%+@=-]{1,512}",app_id): raise HTTPException(400,"Invalid app")
def aggregate(db,app_id):
    n,avg=db.execute("SELECT COUNT(*),COALESCE(AVG(score),0) FROM votes WHERE app=?",(app_id,)).fetchone()
    return {"count":n,"average":round(avg,2)}
def digest(value):
    return hmac.new(SECRET.encode(),value.encode(),hashlib.sha256).hexdigest()
class Vote(BaseModel):
    voter:UUID4
    score:int=Field(ge=1,le=5,strict=True)
@app.get("/health")
def health():return {"ok":True}
@app.get("/v1/ratings/{app_id:path}")
def read(app_id:str):
    valid(app_id)
    with connect() as db:return aggregate(db,app_id)
@app.post("/v1/ratings/{app_id:path}")
def vote(app_id:str,body:Vote,request:Request):
    valid(app_id)
    key=digest("ip:"+str(request.client.host if request.client else "unknown"))
    voter=digest("voter:"+str(body.voter))
    window=int(time.time())//3600
    with connect() as db:
        db.execute("BEGIN IMMEDIATE")
        db.execute("DELETE FROM limits WHERE window<?",(window-1,))
        db.execute("INSERT INTO limits VALUES(?,?,1) ON CONFLICT(key,window) DO UPDATE SET n=n+1",(key,window))
        if db.execute("SELECT n FROM limits WHERE key=? AND window=?",(key,window)).fetchone()[0]>120:
            db.commit();raise HTTPException(429,"Rate limit")
        db.execute("INSERT INTO votes VALUES(?,?,?) ON CONFLICT(app,voter) DO UPDATE SET score=excluded.score",(app_id,voter,body.score))
        return aggregate(db,app_id)
