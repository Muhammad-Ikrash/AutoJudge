# AutoJudge

AutoJudge is a custom assignment grading system inspired by online judges like DOMjudge, Codeforces, and HackerRank. The goal is to automate the collection, execution, and grading of programming assignments submitted by students.

Instead of manually downloading submissions and checking outputs, AutoJudge aims to handle the complete workflow automatically.

---

## Pre-Reqs

- Run RabbitMQ server on your local machine
        - sudo docker run -d --name rabbitmq-autojudge -p 5672:5672 -p 15672:15672 rabbitmq:4.3.2-management-alpine
- create an image from the Docker File in @evaluation_engine with name auto-judge-container:v1.0
        - sudo docker build -t auto-judge-container:v1.0 .


## Project Goals

- Collect submissions from Google Classroom
- Organize submissions by assignment and student
- Execute untrusted code safely inside Docker containers
- Grade submissions against predefined test cases
- Support partial marking
- Process multiple submissions in parallel
- Provide a simple interface for instructors to manage grading

---

Current Progress : Backend Has been completed, only frontend and K8s remains 

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
- Kubernetes
- Angular

---

## Folder Structure

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

---

## Future Features

- Multiple programming language support
- Instructor dashboard
- Kubernetes-based execution workers
- Performance and resource monitoring

---
