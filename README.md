# AutoJudge

AutoJudge is a custom assignment grading system inspired by online judges like DOMjudge, Codeforces, and HackerRank. The goal is to automate the collection, execution, and grading of programming assignments submitted by students.

Instead of manually downloading submissions and checking outputs, AutoJudge aims to handle the complete workflow automatically.

---
## Setup

Run these commands in Order

```
# 1. Clone
git clone https://github.com/Muhammad-Ikrash/AutoJudge.git
cd AutoJudge

# 2. Infrastructure: build the sandbox image, start RabbitMQ
docker compose build sandbox
docker compose up -d rabbitmq

# 3. Backend: build the jar
cd AutoJudge/evaluation_engine
mvn clean package

# 4. Backend: run the API (creds must match the compose file's RABBITMQ_DEFAULT_USER/PASS)
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

For the classroom_scraper to work, you need to set up the Google OAuth credentials. 
create a project on google cloud console
enable the classroom api and drive api
download the credentials.json file
place it in the classroom_scraper folder

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

Current Progress : Only K8s left ALong with Frontend Statelessness Fix

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
├── classroom_scraper/
├── evaluation_engine/
├── backend/
├── frontend/
├── assignments/
└── README.md
```

