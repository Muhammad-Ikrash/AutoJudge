"""
Google Classroom & Drive submission attachments downloader.
"""

import io
import logging
import os
import re
import sys
from typing import Any, Callable, Dict, List, Optional, Tuple

from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import Resource, build
from googleapiclient.http import MediaIoBaseDownload

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s"
)
logger = logging.getLogger(__name__)

os.environ["OAUTHLIB_RELAX_TOKEN_SCOPE"] = "1"

CREDENTIALS_FILE = "credentials.json"
TOKEN_FILE = "token.json"
DEFAULT_DOWNLOAD_ROOT = "./CourseFiles"

GOOGLE_EXPORT_MIME_MAP: Dict[str, Tuple[str, str]] = {
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

SCOPES: List[str] = [
    "https://www.googleapis.com/auth/classroom.courses.readonly",
    "https://www.googleapis.com/auth/classroom.coursework.students.readonly",
    "https://www.googleapis.com/auth/classroom.student-submissions.students.readonly",
    "https://www.googleapis.com/auth/classroom.rosters.readonly",
    "https://www.googleapis.com/auth/classroom.profile.emails",
    "https://www.googleapis.com/auth/drive.readonly",
]


def authenticate() -> Tuple[Resource, Resource]:
    """Authenticates the user and returns Google Classroom and Drive service instances.

    Returns:
        Tuple[Resource, Resource]: (classroom_service, drive_service)
    """
    creds = None

    if os.path.exists(TOKEN_FILE):
        creds = Credentials.from_authorized_user_file(TOKEN_FILE, SCOPES)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            if not os.path.exists(CREDENTIALS_FILE):
                logger.error("'%s' not found. Please place credentials.json in this directory.", CREDENTIALS_FILE)
                sys.exit(1)
            flow = InstalledAppFlow.from_client_secrets_file(CREDENTIALS_FILE, SCOPES)
            creds = flow.run_local_server(port=0)

        with open(TOKEN_FILE, "w", encoding="utf-8") as token:
            token.write(creds.to_json())

    classroom_service = build("classroom", "v1", credentials=creds)
    drive_service = build("drive", "v3", credentials=creds)
    return classroom_service, drive_service


def sanitize(name: Optional[str]) -> str:
    """Sanitizes strings for safe filesystem usage.

    Args:
        name: Raw input string or filename

    Returns:
        str: Sanitized filename safe for disk storage
    """
    if not name:
        return "unnamed"
    sanitized = re.sub(r'[<>:"/\\|?*]', "_", name).strip()
    sanitized = os.path.basename(sanitized)
    return sanitized[:150] if sanitized else "unnamed"


def prompt_select(items: List[Any], label_fn: Callable[[Any], str], prompt_text: str) -> Any:
    """Displays a numbered menu for the user to select an item.

    Args:
        items: List of items to select from
        label_fn: Function mapping an item to a string representation
        prompt_text: Name of the resource being selected

    Returns:
        Selected item from the list
    """
    if not items:
        logger.warning("No %s found.", prompt_text)
        sys.exit(1)

    print(f"\nSelect a {prompt_text}:")
    for i, item in enumerate(items, start=1):
        print(f"  [{i}] {label_fn(item)}")

    while True:
        choice = input(f"\nEnter choice (1-{len(items)}): ").strip()
        if choice.isdigit() and 1 <= int(choice) <= len(items):
            return items[int(choice) - 1]
        print("Invalid choice, please try again.")


def get_student_display_name(
    classroom_service: Resource,
    course_id: str,
    user_id: str,
    name_cache: Dict[str, str]
) -> str:
    """Resolves a Classroom user ID to a human-readable name, caching results.

    Args:
        classroom_service: Authenticated Google Classroom service
        course_id: Unique course ID
        user_id: Unique user ID
        name_cache: Dictionary cache for user ID to name mappings

    Returns:
        str: Full name of student or raw user ID as fallback
    """
    if not user_id:
        return "unknown_student"

    if user_id in name_cache:
        return name_cache[user_id]

    try:
        profile = classroom_service.userProfiles().get(userId=user_id).execute()
        full_name = profile.get("name", {}).get("fullName", user_id)
    except Exception as e:
        logger.warning("Could not resolve display name for user_id '%s': %s", user_id, e)
        full_name = user_id

    name_cache[user_id] = full_name
    return full_name


def get_student_email(
    classroom_service: Resource,
    user_id: str,
    email_cache: Dict[str, str]
) -> str:
    """Resolves a Classroom user ID to the student's email address, caching results.

    Args:
        classroom_service: Authenticated Google Classroom service
        user_id: Unique user ID
        email_cache: Dictionary cache for user ID to email mappings

    Returns:
        str: Student email address, or raw user ID as fallback
    """
    if not user_id:
        return "unknown@unknown"

    if user_id in email_cache:
        return email_cache[user_id]

    try:
        profile = classroom_service.userProfiles().get(userId=user_id).execute()
        email = profile.get("emailAddress", user_id)
    except Exception as e:
        logger.warning("Could not resolve email for user_id '%s': %s", user_id, e)
        email = user_id

    email_cache[user_id] = email
    return email


def parse_folder_name_from_email(email: str) -> str:
    """Derives a student folder name from an institutional email address.

    Expects the local part of the email to follow the pattern ``YXXXXXX``
    where Y is a single alphabetic character and X represents digits
    (e.g. ``L123456@fast.edu.pk``).  The resulting folder name follows
    the format ``XXY-XXXX`` (e.g. ``12L-3456``):

    * Positions 0-1 : first two digits
    * Position  2   : Y (forced to upper-case)
    * Dash separator
    * Positions 3-6 : remaining four digits

    If the local part does not match the expected pattern the sanitized
    local part (or full email) is returned as-is.

    Args:
        email: Student email in the format ``YXXXXXX@domain``

    Returns:
        str: Folder name in ``XXY-XXXX`` format, or a sanitized fallback
    """
    local = email.split("@")[0]
    match = re.fullmatch(r"([A-Za-z])(\d{6})", local)
    if match:
        char_y = match.group(1).upper()
        digits = match.group(2)          # 6 digits
        return f"{digits[0:2]}{char_y}-{digits[2:6]}"

    logger.warning(
        "Email local part '%s' does not match expected pattern YXXXXXX; "
        "using sanitized local part as folder name.",
        local,
    )
    return sanitize(local)


def list_courses(classroom_service: Resource) -> List[Dict[str, Any]]:
    """Lists all active courses where the authenticated user is a teacher.

    Args:
        classroom_service: Authenticated Google Classroom service

    Returns:
        List of active course dictionaries
    """
    courses: List[Dict[str, Any]] = []
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


def list_coursework(classroom_service: Resource, course_id: str) -> List[Dict[str, Any]]:
    """Lists coursework/assignments for a course.

    Args:
        classroom_service: Authenticated Google Classroom service
        course_id: Unique course ID

    Returns:
        List of coursework dictionaries
    """
    coursework: List[Dict[str, Any]] = []
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


def list_submissions(classroom_service: Resource, course_id: str, coursework_id: str) -> List[Dict[str, Any]]:
    """Lists all student submissions for a given assignment.

    Args:
        classroom_service: Authenticated Google Classroom service
        course_id: Unique course ID
        coursework_id: Unique coursework ID

    Returns:
        List of submission dictionaries
    """
    submissions: List[Dict[str, Any]] = []
    page_token = None
    while True:
        response = (
            classroom_service.courses()
            .courseWork()
            .studentSubmissions()
            .list(
                courseId=course_id,
                courseWorkId=coursework_id,
                pageToken=page_token,
            )
            .execute()
        )
        submissions.extend(response.get("studentSubmissions", []))
        page_token = response.get("nextPageToken")
        if not page_token:
            break
    return submissions


def download_drive_file(
    drive_service: Resource,
    file_id: str,
    dest_path: str,
    export_mime_map: Optional[Dict[str, Tuple[str, str]]] = None
) -> str:
    """Downloads a single Google Drive file or exports a Google Doc.

    Args:
        drive_service: Authenticated Google Drive service
        file_id: Drive file ID
        dest_path: Target destination path on disk
        export_mime_map: Optional custom export MIME map

    Returns:
        str: Absolute path of downloaded file
    """
    if export_mime_map is None:
        export_mime_map = GOOGLE_EXPORT_MIME_MAP

    file_meta = drive_service.files().get(fileId=file_id, fields="name,mimeType").execute()
    mime_type = file_meta.get("mimeType", "")

    if mime_type in export_mime_map:
        export_mime, extension = export_mime_map[mime_type]
        if not dest_path.endswith(extension):
            dest_path += extension
        request = drive_service.files().export_media(fileId=file_id, mimeType=export_mime)
    else:
        request = drive_service.files().get_media(fileId=file_id)

    os.makedirs(os.path.dirname(os.path.abspath(dest_path)), exist_ok=True)
    with io.FileIO(dest_path, "wb") as fh:
        downloader = MediaIoBaseDownload(fh, request)
        done = False
        while not done:
            _, done = downloader.next_chunk()

    return dest_path


def download_submission_attachments(
    drive_service: Resource,
    submission: Dict[str, Any],
    dest_folder: str
) -> int:
    """Downloads all Drive file attachments and saves links for a student submission.

    Args:
        drive_service: Authenticated Google Drive service
        submission: Student submission dictionary
        dest_folder: Target directory to save attachments

    Returns:
        int: Number of downloaded attachments
    """
    attachments = submission.get("assignmentSubmission", {}).get("attachments", [])
    if not attachments:
        return 0

    if not os.path.exists(dest_folder):
        os.makedirs(dest_folder, exist_ok=True)

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
                logger.info("  Downloaded: %s", os.path.basename(saved_path))
                downloaded += 1
            except Exception as e:
                logger.error("  FAILED to download '%s': %s", title, e)

        elif link:
            link_url = link.get("url", "")
            with open(os.path.join(dest_folder, "link.txt"), "a", encoding="utf-8") as f:
                f.write(link_url + "\n")
            logger.info("  Saved link: %s", link_url)
            downloaded += 1

        elif youtube:
            yt_url = youtube.get("alternateLink", "")
            with open(os.path.join(dest_folder, "youtube_link.txt"), "a", encoding="utf-8") as f:
                f.write(yt_url + "\n")
            logger.info("  Saved YouTube link: %s", yt_url)
            downloaded += 1

    return downloaded


def main(download_root: str = DEFAULT_DOWNLOAD_ROOT) -> None:
    """Main CLI entry point for scraping attachments."""
    classroom_service, drive_service = authenticate()

    courses = list_courses(classroom_service)
    course = prompt_select(courses, lambda c: c.get("name", "Untitled course"), "course")
    course_id = course["id"]
    course_name = sanitize(course.get("name", course_id))

    logger.info("Fetching assignments for '%s'...", course.get("name", course_id))
    coursework_list = list_coursework(classroom_service, course_id)
    coursework = prompt_select(
        coursework_list,
        lambda cw: f"{cw.get('title', 'Untitled assignment')} [{cw.get('state', 'UNKNOWN')}]",
        "assignment",
    )
    coursework_id = coursework["id"]
    assignment_name = sanitize(coursework.get("title", coursework_id))

    logger.info("Fetching submissions for '%s'...", coursework.get("title", coursework_id))
    submissions = list_submissions(classroom_service, course_id, coursework_id)
    logger.info("Found %d submission(s).", len(submissions))

    assignment_root = os.path.join(download_root, course_name, assignment_name)
    os.makedirs(assignment_root, exist_ok=True)

    email_cache: Dict[str, str] = {}
    total_files = 0

    for i, submission in enumerate(submissions, start=1):
        user_id = submission.get("userId", "")
        state = submission.get("state", "UNKNOWN")

        student_email = get_student_email(classroom_service, user_id, email_cache)
        student_folder_name = parse_folder_name_from_email(student_email)
        student_folder = os.path.join(assignment_root, student_folder_name)

        logger.info("[%d/%d] %s (state: %s)", i, len(submissions), student_email, state)

        if state not in ("TURNED_IN", "RETURNED"):
            logger.info("  No submission to download, skipping.")
            continue

        count = download_submission_attachments(drive_service, submission, student_folder)
        if count == 0:
            logger.info("  No attachments found.")
        total_files += count

    logger.info("Done. Downloaded %d file(s)/link(s) into:\n  %s", total_files, os.path.abspath(assignment_root))


if __name__ == "__main__":
    main()