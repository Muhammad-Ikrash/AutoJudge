# AutoJudge Code Review - Milestone 2
**Rating: 6.5/10**

---

## Executive Summary
The codebase shows decent architectural thinking with clear separation of concerns in the Java evaluation engine. However, it suffers from several critical issues: massive god classes doing too much, dangerous hardcoded magic numbers and strings scattered throughout, weak Python code, inadequate logging, and no testing infrastructure. The code "works" but is fragile and difficult to maintain.

---

## Major Issues

### 1. **DockerRunner Class - Bloated God Class** (CRITICAL)
**Location:** `DockerRunner.java` (360 lines)
**Severity:** 🔴 Critical

The `DockerRunner` class violates the Single Responsibility Principle egregiously:
- Container lifecycle management (create, start, stop, destroy)
- Submission compilation
- Test case execution
- Output validation
- File system operations
- Malicious code detection
- Verdict determination
- Result building
- Error handling for multiple scenarios (compilation, execution, internal errors)
- Custom logging implementation

**Problems:**
```java
// This method orchestrates EVERYTHING - 37 lines
public List<ExecutionResult> runSubmission(ContainerConfig config, Submission submission, List<TestCase> testCases)

// Multiple responsibilities in one method
private ExecutionResult executeSingleTestCase(...) // 42 lines doing:
// - Input file resolution
// - File copying
// - Execution timing
// - Unauthorized file detection
// - Truncation checking
// - Verdict determination
```

**Impact:** Any change to one aspect requires modifying this massive class. Testing is nearly impossible. Reusability is zero.

**Recommendation:** Split into:
- `CompilationOrchestrator`
- `TestCaseExecutor`
- `MaliciousCodeDetector`
- `OutputValidator`

---

### 2. **Main.java - Spaghetti Configuration & Parsing Logic** (CRITICAL)
**Location:** `Main.java` (251 lines)
**Severity:** 🔴 Critical

The `Main` class mixes concerns badly:
- Command line argument parsing (lines 44-46, 48-70)
- File system operations (lines 117-136, 174-184)
- Test case loading and matching (lines 138-172)
- Weight file parsing with regex (lines 196-225)
- Test case normalization (lines 186-194)
- The `loadTestCases()` method is a nightmare:

```java
private static List<TestCase> loadTestCases(...) throws IOException {
    // Lines 153-172: Complex logic matching input/output files
    // Multiple passes over the same data
    // Magic string replacements: "test_input", "input", "test_output", "output"
    // Fallback logic that's hard to reason about
    // Weight lookup with getOrDefault chains
}
```

**Problems:**
- String normalization with `.replace()` chains is brittle
- No validation that matched input/output pairs make sense
- Regex parsing of weights is fragile
- No error reporting when files don't match
- `normalizeStem()` strips meaningful information ("test1" → "1" → "")

**Recommendation:** Extract to dedicated loader classes:
```java
// New classes:
- WeightsFileParser (handles JSON, CSV, etc.)
- TestCaseFileProcessor (matches I/O pairs)
- ArgumentValidator
- ConfigurationBuilder
```

---

### 3. **Magic Numbers & Hardcoded Constants Everywhere** (CRITICAL)
**Severity:** 🔴 Critical

Scattered throughout codebase:
```java
// DockerRunner.java
private static final int MAX_STREAM_BYTES = 1_000_000_000;  // Where did this come from?
// Line 350: Thread.currentThread().getStackTrace()[2] - magic index
// Line 256-260: Output truncation logic duplicated

// DockerConstants.java
DEFAULT_COMPILE_TIMEOUT_SEC = 30 (hardcoded, can't override)
SIGKILL_GRACE_PERIOD = 1 (hardcoded, can't override)
MAX_OUTPUT_BYTES = 1_000_000 (hardcoded, can't override)

// ContainerManager.java (Line 200)
long safetyNetTimeout = timeoutSeconds > 0 ? timeoutSeconds + 5 : 65;
// Why +5? Why 65? Why these specific numbers?

// SubmissionScanner.java (Lines 89-101)
Multiple hardcoded file extension checks
```

**Problems:**
- No explanation for values
- Impossible to tune without code changes
- No configuration mechanism
- Scattered across multiple files

**Recommendation:** Centralize in configuration:
```java
@Configuration
public class SystemConfig {
    @Value("${docker.stream.max-bytes:1000000000}")
    long maxStreamBytes;
    
    @Value("${compile.timeout-sec:30}")
    int compileTimeout;
    // etc.
}
```

---

### 4. **Custom Logging Implementation - TERRIBLE IDEA** (MAJOR)
**Location:** `DockerRunner.java` lines 348-359
**Severity:** 🟠 Major

```java
private static void log(String message) {
    StackTraceElement frame = Thread.currentThread().getStackTrace()[2];
    System.out.println("[" + frame.getFileName() + ":" + frame.getLineNumber() + "] " + message);
}
```

**Problems:**
- Using `getStackTrace()[2]` is fragile and slow (parsing stack trace on every log!)
- No log levels (debug, info, warn, error)
- No timestamp
- No thread information
- System.out/System.err mixed for logging
- Not configurable
- Cannot redirect to file or structured logging
- Calling `Thread.currentThread().getStackTrace()` on every single log call is a performance killer

**Recommendation:** Use industry standard:
```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
</dependency>
```

---

### 5. **GradingService - Complex Verdict Logic Not Tested** (MAJOR)
**Location:** `GradingService.java`
**Severity:** 🟠 Major

```java
private int getSeverityRank(Verdict verdict) {
    return switch (verdict) {
        case ACCEPTED -> 0;
        case WRONG_ANSWER -> 1;
        case RUNTIME_ERROR -> 2;
        case TIME_LIMIT_EXCEEDED -> 3;
        case MEMORY_LIMIT_EXCEEDED -> 4;
        case COMPILATION_ERROR -> 5;
        case INTERNAL_ERROR -> 6;
        case MALICIOUS_CODE -> 7;
    };
}
```

**Problems:**
- Hardcoded severity ranking logic with no documentation
- Why is MALICIOUS_CODE > INTERNAL_ERROR?
- What if we add new verdicts? Must update method
- `resolveWorseVerdict()` logic is ternary-based and hard to read
- Verdict hierarchy should be defined in enum, not scattered method

**Recommendation:**
```java
public enum Verdict {
    ACCEPTED(0),
    WRONG_ANSWER(1),
    RUNTIME_ERROR(2),
    TIME_LIMIT_EXCEEDED(3),
    MEMORY_LIMIT_EXCEEDED(4),
    COMPILATION_ERROR(5),
    INTERNAL_ERROR(6),
    MALICIOUS_CODE(7);
    
    private final int severity;
    
    Verdict(int severity) {
        this.severity = severity;
    }
    
    public boolean isMoreSevereThan(Verdict other) {
        return this.severity > other.severity;
    }
}
```

---

### 6. **No Proper Dependency Injection or Configuration** (MAJOR)
**Severity:** 🟠 Major

```java
// Main.java
DockerRunner dockerRunner = new DockerRunner();  // Tightly coupled
GradingService gradingService = new GradingService();  // Tightly coupled
```

**Problems:**
- Cannot inject test doubles for testing
- Cannot swap implementations
- DockerRunner instantiates its own ContainerManager
- ContainerManager instantiates its own DockerClient
- Hard to test without running actual Docker

**Recommendation:** Use constructor injection:
```java
public class GradingOrchestrator {
    private final DockerRunner dockerRunner;
    private final GradingService gradingService;
    
    public GradingOrchestrator(DockerRunner dockerRunner, GradingService gradingService) {
        this.dockerRunner = dockerRunner;
        this.gradingService = gradingService;
    }
}
```

---

### 7. **Python Script - Poor Quality** (MAJOR)
**Location:** `ScrapingAttachments.py`
**Severity:** 🟠 Major

```python
################################################################
# Just Helping FUnctions, Will be chagned later                #
################################################################
# ^ Typos in comment, "changed" misspelled, unprofessional tone

# Line 82: "idk man, AI gave this function"
# ^ Not a reason to include code without review

# Line 97: No docstring for get_student_display_name
# Line 181: No validation of file paths
# Line 204: No error handling for attachment processing
```

**Problems:**
- Unprofessional comments referencing AI
- No type hints despite Python 3.5+
- No validation of OAuth credentials before using
- Error handling is `except Exception:` (catches everything)
- Global configuration at module level (`DOWNLOAD_ROOT`, etc.)
- No logging
- No way to configure output directory
- Hard to test

**Recommendation:**
```python
from typing import Dict, Optional
import logging

logger = logging.getLogger(__name__)

def download_submission_attachments(
    drive_service: DriveService,
    submission: Dict,
    dest_folder: str
) -> int:
    """Download all Drive file attachments from a submission.
    
    Args:
        drive_service: Authenticated Google Drive service
        submission: Student submission dict from Classroom API
        dest_folder: Directory to save files
        
    Returns:
        Number of files downloaded
        
    Raises:
        ValueError: If destination folder is invalid
        IOError: If download fails
    """
    # ... implementation
```

---

## Medium Issues

### 8. **Duplication: Error Result Building** (MEDIUM)
**Location:** `DockerRunner.java` lines 116-138, 296-316, 318-335
**Severity:** 🟡 Medium

Three nearly identical methods that build error ExecutionResults:
```java
private List<ExecutionResult> buildCompilationErrorResults(...)  // ~22 lines
private void appendMaliciousCodeAbortionResults(...)  // ~16 lines
private List<ExecutionResult> buildInternalErrorResults(...)    // ~16 lines
```

All do essentially the same thing: iterate test cases, create ExecutionResult with error verdict.

**Recommendation:** Extract factory method:
```java
private List<ExecutionResult> buildErrorResults(
    List<TestCase> testCases,
    Verdict verdict,
    String errorMessage
) {
    return testCases.stream()
        .map(tc -> new ExecutionResult(
            tc.id(),
            verdict,
            "",
            "",
            errorMessage,
            -1,
            0,
            0
        ))
        .toList();
}
```

---

### 9. **String Matching Logic for File Extensions** (MEDIUM)
**Location:** `SubmissionScanner.java` lines 89-111
**Severity:** 🟡 Medium

```java
// Duplicated logic for detecting and supporting files
private static Language detectLanguageFromFileName(String fileName) {
    if (fileName.endsWith(".cpp") || fileName.endsWith(".cc") || ...) return Language.CPP;
    if (fileName.endsWith(".c")) return Language.C;
    // ... repeated checks
}

private static boolean isSupportedSourceFile(String fileName) {
    return fileName.endsWith(".cpp") || fileName.endsWith(".cc") || ...;
    // Same checks again!
}
```

**Problems:**
- Duplicated logic
- Adding new language requires 2+ changes
- No separation between "how to identify" and "what's supported"

**Recommendation:** Use Language enum for metadata:
```java
public enum Language {
    CPP(List.of(".cpp", ".cc", ".cxx", ".hpp")),
    C(List.of(".c")),
    JAVA(List.of(".java")),
    PYTHON(List.of(".py"));
    
    private final List<String> extensions;
    
    public static Language fromFileName(String fileName) {
        String lower = fileName.toLowerCase();
        return Arrays.stream(values())
            .filter(lang -> lang.extensions.stream()
                .anyMatch(lower::endsWith))
            .findFirst()
            .orElse(null);
    }
}
```

---

### 10. **Output Truncation Logic Duplicated** (MEDIUM)
**Location:** `DockerRunner.java` lines 256-262
**Severity:** 🟡 Medium

```java
if (stdout.length() > DockerConstants.MAX_OUTPUT_BYTES) {
    stdout = stdout.substring(0, DockerConstants.MAX_OUTPUT_BYTES) + "\n[Output truncated]";
}
if (stderr.length() > DockerConstants.MAX_OUTPUT_BYTES) {
    stderr = stderr.substring(0, DockerConstants.MAX_OUTPUT_BYTES) + "\n[Output truncated]";
}
```

Same logic twice. Should be:
```java
private String truncateIfNeeded(String output) {
    if (output.length() > DockerConstants.MAX_OUTPUT_BYTES) {
        return output.substring(0, DockerConstants.MAX_OUTPUT_BYTES) + "\n[Output truncated]";
    }
    return output;
}
```

---

### 11. **Null Checking Paranoia** (MEDIUM)
**Severity:** 🟡 Medium

Inconsistent null handling:
```java
// Sometimes:
if (testCase == null) continue;

// Sometimes:
if (testCase != null && testCase.id() != null) {
    map.putIfAbsent(testCase.id(), testCase);
}

// Sometimes:
String student_name = get_student_display_name(...)
// No null check before using it
```

**Problems:**
- Defensive checks scatter throughout code
- Unclear when nulls are acceptable
- Some places assume non-null without checking

**Recommendation:** Define null handling policy. Use `@NotNull` / `@Nullable` annotations (JSR 305).

---

### 12. **No Configuration File Support** (MEDIUM)
**Severity:** 🟡 Medium

The system requires 5 command-line arguments. No config file, no environment variable support, no defaults. Inflexible.

---

## Minor Issues

### 13. **Missing Javadoc Comments**
Most methods lack documentation. `ContainerManager` is especially bad - complex Docker operations with no explanations.

### 14. **Hardcoded File Extensions in Python Script**
```python
GOOGLE_EXPORT_MIME_MAP = {
    "application/vnd.google-apps.document": (
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ".docx",
    ),
    # ...
}
```
Should be configurable.

### 15. **Record Usage in GradingService**
```java
private record EvaluationGrade(int passedTests, int earnedWeight, Verdict finalVerdict) {}
```
Good use of records, but this should be extracted as a public type if used elsewhere, or better yet, just return values directly or use a proper DTO.

### 16. **Exception Suppression in Container Cleanup**
```java
finally {
    destroyContainer(containerId);  // Exceptions here are silently swallowed
}
```
Should log failures even if swallowing exceptions.

### 17. **Test Case Matching Algorithm is Fragile**
Lines 154-169 in Main.java: The test case matching tries three different strategies. This suggests the current file naming convention isn't enforced. Needs better documentation.

### 18. **No Input Validation**
Loaders don't validate that:
- Files actually exist before returning paths
- Directories are readable/writable before using them
- Configuration values are sensible

---

## What's Done Well ✅

1. **Architecture is mostly sound** - Separation into packages (docker, grading, compiler, loader, model)
2. **Records for data classes** - Using Java records for models is modern
3. **Docker integration** - Actually works with dockerjava library
4. **Execution isolation** - Good thinking on timeout and malicious code detection
5. **Partial grading** - Weight-based scoring is implemented
6. **Compiler abstraction** - Decent support for multiple languages

---

## Testing

**Status: ZERO tests found** 🔴

No JUnit tests exist. This is critical for confidence in grading logic.

**Recommendation - Priority 1:**
```java
@DisplayName("Grading Service Tests")
class GradingServiceTest {
    
    @Test
    void testAllTestsPassed() {
        // Should award full points
    }
    
    @Test
    void testPartialPassWithWeights() {
        // Should calculate score correctly
    }
    
    @Test
    void testMaliciousCodeZerosScore() {
        // Malicious code should always be 0
    }
}
```

---

## Security Concerns 🔐

1. **File system traversal** - No validation that test case paths are within expected directory
2. **Regex DoS** - `WEIGHT_JSON_PATTERN` could be exploited with malicious input
3. **Process execution** - Commands are built with string concatenation (though Docker isolation helps)
4. **OAuth credentials** - Stored in `token.json`, not encrypted
5. **No rate limiting** - Could be abused to consume resources
6. **Container escape** - Relying entirely on Docker isolation, no additional checks

---

## Recommendations Priority

### Priority 1 (Do Now)
- [ ] Add SLF4j logging (replace custom log methods)
- [ ] Extract magic numbers to configuration
- [ ] Add unit tests for grading logic
- [ ] Split DockerRunner into smaller classes

### Priority 2 (Before Production)
- [ ] Add comprehensive Javadoc
- [ ] Extract Python scraper concerns (config, logging, error handling)
- [ ] Add input validation and error messages
- [ ] Implement proper CLI argument parsing (use picocli or similar)

### Priority 3 (Nice to Have)
- [ ] Add structured logging with timestamps
- [ ] Configuration file support (YAML/JSON)
- [ ] Metrics/monitoring integration
- [ ] Caching for Docker image pulls
- [ ] Parallel submission processing

---

## Lines of Code Summary

| Module | LOC | Status |
|--------|-----|--------|
| Java Engine | 1,646 | Functional but needs refactoring |
| Python Scraper | 301 | Works but needs polish |
| Tests | 0 | **CRITICAL GAP** |
| Configuration | Minimal | Missing |

---

## Final Notes

The code shows you understand the problem domain and can write working solutions. The execution engine fundamentally works and compiles/executes code correctly. However, it has serious architectural issues that will become painful as you scale:

1. The god classes will be impossible to test
2. Configuration is too rigid for different environments
3. Custom logging defeats the purpose of having a logging framework
4. Scattered magic numbers will cause bugs during maintenance
5. No tests mean refactoring is risky

**The code is "glue and go" - functional but brittle.** Before moving to Milestone 3 (multi-language, parallel processing), fix these structural issues or you'll be rewriting core components.

**Rating Justification: 6.5/10**
- Functionality works: +3
- Architecture has sound concepts: +2
- Good use of modern Java (records): +0.5
- Missing tests: -1
- God classes & code duplication: -1
- Poor configuration: -0.5
- Weak Python code: -0.5
