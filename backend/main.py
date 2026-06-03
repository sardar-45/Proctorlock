from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

violations = []
results = []

class Violation(BaseModel):
    student: str
    violation: str
    count: int

class Result(BaseModel):
    student: str
    score: int

@app.get("/")
def home():
    return {
        "message": "ProctorLock Backend Running"
    }

@app.post("/violation")
def add_violation(data: Violation):

    violations.append(data.dict())

    print("====================")
    print("STUDENT:", data.student)
    print("VIOLATION:", data.violation)
    print("COUNT:", data.count)
    print("====================")

    return {
        "status": "saved"
    }

@app.get("/violations")
def get_violations():
    return violations

@app.post("/result")
def add_result(data: Result):

    results.append(data.dict())

    print("RESULT SAVED")

    return {
        "status": "result saved"
    }

@app.get("/results")
def get_results():
    return results