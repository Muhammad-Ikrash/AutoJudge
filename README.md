# AutoJudge

AutoJudge is a custom assignment grading system inspired by online judges like DOMjudge, Codeforces, and HackerRank. The goal is to automate the collection, execution, and grading of programming assignments submitted by students.

Instead of manually downloading submissions and checking outputs, AutoJudge aims to handle the complete workflow automatically.

See these in Order
[Setup](#setup) Instructions

[Notice](#notice)

---

## Setup

Run these commands in Order

```bash
# 1. Clone
git clone https://github.com/Muhammad-Ikrash/AutoJudge.git
cd AutoJudge

# 2. Infrastructure: build the sandbox image, start RabbitMQ
docker compose build sandbox
docker compose up -d rabbitmq-autojudge

# 3. Backend: build the jar
cd AutoJudge/evaluation_engine
mvn clean package

# 4. Backend: run the API
#    Credentials must match docker-compose.yaml's RABBITMQ_DEFAULT_USER/PASS
RABBITMQ_USERNAME=autojudge RABBITMQ_PASSWORD=autojudge \
  java -jar target/evaluation_engine-1.0-SNAPSHOT.jar api
#   ^ leave this running in its own terminal — API comes up on :8080

# 5. Frontend: install deps and serve (new terminal, back at repo root)
cd AutoJudge/front_end
npm install
ng serve
#   ^ leave this running too — opens on :4200
```

---

## Notice

1) For the classroom_scraper to work, you need to set up the Google OAuth credentials.
   create a project on google cloud console
   enable the classroom api and drive api
   download the credentials.json file
   place it in the classroom_scraper folder

2) The Assignment folder requires a specific format to run, as follows:

   ```
   <assignment-root>/
   ├── config.json
   ├── weights.json
   ├── input/
   │   ├── 1.txt
   │   ├── 2.txt
   │   └── 3.txt
   ├── expected/
   │   ├── 1.txt
   │   ├── 2.txt
   │   └── 3.txt
   └── submissions/
       ├── <studentId1>/
       │   └── solution.cpp         
       ├── <studentId2>/
       │   └── Solution.cpp
       └── <studentId3>/
           └── main.py
   ```

   - **`input/` and `expected/`** — one file per test case in each folder. Files are paired by matching filename stem (`input/1.txt` ↔ `expected/1.txt`), extension doesn't need to match. If no output file matches a given input by name, pairing falls back to file order — name them consistently to avoid relying on that.
   - **`submissions/`** — one folder per student, folder name = student ID. Each folder holds that student's source file(s); a submission can span multiple files but **all files in one submission must be the same language** — mixing languages within a single submission is rejected.
   - **Supported languages / extensions** — C++ (`.cpp`, `.cc`, `.cxx`, `.hpp`, `.h`), C (`.c`), Python (`.py`). Language is auto-detected per submission from file extensions; no per-assignment language flag is needed.
   - **`config.json`** — grading/runtime config for the whole assignment. Sample:

     ```json
        {
                "assignmentId" : "assignment-1",          
                "resourceLimits" : {
                        "timeLimitMs" : 1000,
                        "memoryLimitMb" : 512,
                        "cpuLimit" : 1.0
                },
                "executionProfile" : {
                        "autoRemove" : true,
                        "workingDirectory" : "/workspace"
                }
        }
     ```

   - **`weights.json`** — per-test-case marks, keyed by the same stem used in `input/`/`expected/`. Any test case not listed here defaults to a weight of `1`. Sample:

     ```json
     {
       "1": 10,
       "2": 10,
       "3": 20
     }
     ```

3) Also for New Submission on Frontend, the browser doesn't allow absolute paths to be recorded in the application.
   To bypass that, copy and paste the absolute path to the assignment root in the form field.

---

## Project Goals

- Collect submissions from Google Classroom
- Organize submissions by assignment and student
- Execute untrusted code safely inside Docker containers
- Grade submissions against predefined test cases
- Support partial marking
- Process multiple submissions in parallel
- Provide a simple interface for instructors to manage grading

---

Current Progress : Only K8s left Along with Frontend Statelessness Fix

---

## Planned Architecture

```
Google Classroom
        │
        ▼
Submission Collector
        │
        ▼
Organized Submissions
        │
        ▼
Docker Sandbox
        │
        ▼
Grading Engine
        │
        ▼
Results Database
        │
        ▼
Angular Dashboard
```

---

## Tech Stack

- Python
- Google Classroom API
- Google Drive API
- Docker
- Spring Boot
- RabbitMQ
- Kubernetes (Future)
- Angular

---

## Folder Structure (Current)

```
AutoJudge/
│
├── classroom_Scraper/
├── evaluation_engine/
├── front_end/
└── README.md
```