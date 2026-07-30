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

Current Progress : Scraped GCR using Google API for Classroom and Drive
