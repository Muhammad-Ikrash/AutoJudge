"""
Google Classroom Assignment Downloader
---------------------------------------
Lists the courses you teach -> lets you pick one -> lists its assignments ->
lets you pick one -> downloads every student's submitted attachments into:

    ./<subject>/<assignment>/<student_name>/<attachment_filename>

SETUP
-----
1. pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib

2. Put your OAuth "Desktop app" credentials.json (from Google Cloud Console)
   in the same folder as this script.

3. In Google Cloud Console, make sure these APIs are enabled on your project:
       - Google Classroom API
       - Google Drive API

4. Run: python classroom_downloader.py
   A browser window will open for the first-time login/consent. After that,
   a token.json is cached so you won't have to log in again.

NOTE ON SCOPES
--------------
This script requests read-only scopes only. It cannot modify grades,
courses, or files on Drive -- it only lists and downloads.
"""

import io
import os
import re
import sys

# Without this, oauthlib raises a hard error (crashing the script) if Google
# grants a slightly different scope list than requested -- which happens if
# a scope isn't registered on your OAuth consent screen in Cloud Console.
# This makes it a non-fatal warning instead so the flow can still complete;
# any API call that actually needed the missing scope will then fail with a
# clear 403 instead of the whole login crashing.
os.environ["OAUTHLIB_RELAX_TOKEN_SCOPE"] = "1"

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseDownload

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

SCOPES = [
    "https://www.googleapis.com/auth/classroom.courses.readonly",
    "https://www.googleapis.com/auth/classroom.coursework.students.readonly",
    "https://www.googleapis.com/auth/classroom.student-submissions.students.readonly",
    "https://www.googleapis.com/auth/classroom.rosters.readonly",
    "https://www.googleapis.com/auth/classroom.profile.emails",
    "https://www.googleapis.com/auth/drive.readonly",
]

CREDENTIALS_FILE = "credentials.json"
TOKEN_FILE = "token.json"
DOWNLOAD_ROOT = "."

# Google Workspace files can't be downloaded directly -- they must be
# "exported" into a real file format instead.
GOOGLE_EXPORT_MIME_MAP = {
    "application/vnd.google-apps.document": (
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ".docx",
    ),
    "application/vnd.google-apps.spreadsheet": (
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ".xlsx",
    ),
    "application/vnd.google-apps.presentation": (
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        ".pptx",
    ),
}


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

def authenticate():
    """Handles the OAuth flow and returns authenticated Classroom + Drive services."""
    creds = None

    if os.path.exists(TOKEN_FILE):
        creds = Credentials.from_authorized_user_file(TOKEN_FILE, SCOPES)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            if not os.path.exists(CREDENTIALS_FILE):
                sys.exit(
                    f"ERROR: '{CREDENTIALS_FILE}' not found. Put your OAuth "
                    f"credentials.json in this folder and try again."
                )
            flow = InstalledAppFlow.from_client_secrets_file(CREDENTIALS_FILE, SCOPES)
            creds = flow.run_local_server(port=0)

        with open(TOKEN_FILE, "w") as token:
            token.write(creds.to_json())

    classroom_service = build("classroom", "v1", credentials=creds)
    drive_service = build("drive", "v3", credentials=creds)
    return classroom_service, drive_service


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def sanitize(name: str) -> str:
    """Makes a string safe to use as a folder/file name across OSes."""
    if not name:
        return "unnamed"
    name = re.sub(r'[<>:"/\\|?*]', "_", name).strip()
    return name[:150] if name else "unnamed"


def prompt_select(items, label_fn, prompt_text):
    """Prints a numbered list and returns the item the user picks."""
    if not items:
        sys.exit(f"No {prompt_text} found. Nothing to do.")

    for i, item in enumerate(items, start=1):
        print(f"  [{i}] {label_fn(item)}")

    while True:
        choice = input(f"\nSelect a {prompt_text} (number): ").strip()
        if choice.isdigit() and 1 <= int(choice) <= len(items):
            return items[int(choice) - 1]
        print("Invalid choice, try again.")


# ---------------------------------------------------------------------------
# Classroom API calls
# ---------------------------------------------------------------------------

def list_courses(classroom_service):
    """Lists all ACTIVE courses the authenticated teacher has access to."""
    courses = []
    page_token = None
    while True:
        response = (
            classroom_service.courses()
            .list(teacherId="me", courseStates=["ACTIVE"], pageToken=page_token)
            .execute()
        )
        courses.extend(response.get("courses", []))
        page_token = response.get("nextPageToken")
        if not page_token:
            break
    return courses


def list_coursework(classroom_service, course_id):
    """Lists all assignments (coursework) for a given course.

    NOTE: the API defaults to returning only PUBLISHED coursework if
    courseWorkStates isn't specified -- DRAFT and DELETED items get silently
    excluded even though a teacher is allowed to see them. We explicitly ask
    for all three states here.
    """
    coursework = []
    page_token = None
    while True:
        response = (
            classroom_service.courses()
            .courseWork()
            .list(
                courseId=course_id,
                courseWorkStates=["PUBLISHED", "DRAFT"],
                pageToken=page_token,
            )
            .execute()
        )
        coursework.extend(response.get("courseWork", []))
        page_token = response.get("nextPageToken")
        if not page_token:
            break
    return coursework


def list_submissions(classroom_service, course_id, coursework_id):
    """Lists all student submissions for a given assignment."""
    submissions = []
    page_token = None
    while True:
        response = (
            classroom_service.courses()
            .courseWork()
            .studentSubmissions()
            .list(courseId=course_id, courseWorkId=coursework_id, pageToken=page_token)
            .execute()
        )
        submissions.extend(response.get("studentSubmissions", []))
        page_token = response.get("nextPageToken")
        if not page_token:
            break
    return submissions


def get_student_display_name(classroom_service, course_id, user_id, name_cache):
    """Resolves a userId to a human-readable name, with caching."""
    if user_id in name_cache:
        return name_cache[user_id]

    try:
        profile = classroom_service.userProfiles().get(userId=user_id).execute()
        full_name = profile.get("name", {}).get("fullName", user_id)
    except Exception:
        full_name = user_id  # fall back to the raw ID if lookup fails

    name_cache[user_id] = full_name
    return full_name


# ---------------------------------------------------------------------------
# Drive download
# ---------------------------------------------------------------------------

def download_drive_file(drive_service, file_id, dest_path):
    """Downloads (or exports, for Google Docs/Sheets/Slides) a single Drive file."""
    file_meta = drive_service.files().get(fileId=file_id, fields="name,mimeType").execute()
    mime_type = file_meta.get("mimeType", "")

    if mime_type in GOOGLE_EXPORT_MIME_MAP:
        export_mime, extension = GOOGLE_EXPORT_MIME_MAP[mime_type]
        if not dest_path.endswith(extension):
            dest_path += extension
        request = drive_service.files().export_media(fileId=file_id, mimeType=export_mime)
    else:
        request = drive_service.files().get_media(fileId=file_id)

    os.makedirs(os.path.dirname(dest_path), exist_ok=True)
    with io.FileIO(dest_path, "wb") as fh:
        downloader = MediaIoBaseDownload(fh, request)
        done = False
        while not done:
            _, done = downloader.next_chunk()

    return dest_path


def download_submission_attachments(drive_service, submission, dest_folder):
    """Downloads every driveFile attachment on a single student submission."""
    attachments = submission.get("assignmentSubmission", {}).get("attachments", [])

    if not attachments:
        return 0

    downloaded = 0
    for attachment in attachments:
        drive_file = attachment.get("driveFile")
        link = attachment.get("link")
        youtube = attachment.get("youTubeVideo")

        if drive_file:
            file_id = drive_file.get("id")
            title = sanitize(drive_file.get("title", file_id))
            dest_path = os.path.join(dest_folder, title)
            try:
                saved_path = download_drive_file(drive_service, file_id, dest_path)
                print(f"      downloaded: {os.path.basename(saved_path)}")
                downloaded += 1
            except Exception as e:
                print(f"      FAILED to download {title}: {e}")

        elif link:
            os.makedirs(dest_folder, exist_ok=True)
            with open(os.path.join(dest_folder, "link.txt"), "a") as f:
                f.write(link.get("url", "") + "\n")
            print(f"      saved link: {link.get('url')}")
            downloaded += 1

        elif youtube:
            os.makedirs(dest_folder, exist_ok=True)
            with open(os.path.join(dest_folder, "youtube_link.txt"), "a") as f:
                f.write(youtube.get("alternateLink", "") + "\n")
            downloaded += 1

    return downloaded


# ---------------------------------------------------------------------------
# Main flow
# ---------------------------------------------------------------------------

def main():
    print("Authenticating with Google...\n")
    classroom_service, drive_service = authenticate()

    # 1. Pick a course
    print("Fetching your courses...")
    courses = list_courses(classroom_service)
    course = prompt_select(courses, lambda c: c.get("name", "Untitled course"), "course")
    course_id = course["id"]
    course_name = sanitize(course.get("name", course_id))

    # 2. Pick an assignment
    print(f"\nFetching assignments for '{course['name']}'...")
    coursework_list = list_coursework(classroom_service, course_id)
    coursework = prompt_select(
        coursework_list,
        lambda cw: f"{cw.get('title', 'Untitled assignment')}  [{cw.get('state', 'UNKNOWN')}]",
        "assignment",
    )
    coursework_id = coursework["id"]
    assignment_name = sanitize(coursework.get("title", coursework_id))

    # 3. Fetch submissions
    print(f"\nFetching submissions for '{coursework['title']}'...")
    submissions = list_submissions(classroom_service, course_id, coursework_id)
    print(f"Found {len(submissions)} submission(s).\n")

    assignment_root = os.path.join(DOWNLOAD_ROOT, course_name, assignment_name)
    os.makedirs(assignment_root, exist_ok=True)

    name_cache = {}
    total_files = 0

    for i, submission in enumerate(submissions, start=1):
        user_id = submission.get("userId")
        state = submission.get("state", "UNKNOWN")

        student_name = get_student_display_name(classroom_service, course_id, user_id, name_cache)
        student_folder_name = sanitize(f"{student_name}_{user_id[:6]}")
        student_folder = os.path.join(assignment_root, student_folder_name)

        print(f"[{i}/{len(submissions)}] {student_name} (state: {state})")

        if state not in ("TURNED_IN", "RETURNED"):
            print("      no submission to download, skipping.")
            continue

        count = download_submission_attachments(drive_service, submission, student_folder)
        if count == 0:
            print("      no attachments found.")
        total_files += count

    print(f"\nDone. Downloaded {total_files} file(s)/link(s) into:\n  {os.path.abspath(assignment_root)}")


if __name__ == "__main__":
    main()