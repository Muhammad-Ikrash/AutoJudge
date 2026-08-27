# AutoJudge

AutoJudge is a custom assignment grading system inspired by online judges like DOMjudge, Codeforces, and HackerRank. The goal is to automate the collection, execution, and grading of programming assignments submitted by students.

Instead of manually downloading submissions and checking outputs, AutoJudge aims to handle the complete workflow automatically.

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

Current Progress : Plagiarism Added

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

## Folder Structure (Planned)

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

- Partial marking
- Multiple programming language support
- Parallel grading
- Plagiarism detection
- Export grades to CSV/Excel
- Instructor dashboard
- Kubernetes-based execution workers
- Performance and resource monitoring

---
