# Contributing to AANO Notes

Thank you for your interest in contributing to AANO! AANO is a free, open-source, privacy-first cross-platform note-taking suite. We welcome contributions from everyone — whether you are fixing a typo, designing an icon, reporting a bug, or writing code for Android and Windows.

---

## How Can You Contribute?

You do not need to be an experienced developer to help improve AANO:
- **Report Bugs**: Found something broken? [Open an Issue](https://github.com/jamamjadalone/AANO/issues) with reproduction steps.
- **Suggest Features**: Have an idea for AANO Drop, UI themes, or encryption? Open a feature request.
- **Improve Docs & Translations**: Fix typos or translate the app and documentation into more languages.
- **Write Code**: Fix existing issues, optimize performance, or build new capabilities.

---

## Step-by-Step Guide to Contributing Code

### Step 1: Fork the Repository
1. Click the **Fork** button at the top-right of the [AANO Repository](https://github.com/jamamjadalone/AANO).
2. Clone your fork to your computer:
   ```bash
   git clone https://github.com/<your-username>/AANO.git
   cd AANO
   ```

### Step 2: Create a Feature Branch
Always create a new branch for your changes instead of working directly on `main`:
```bash
git checkout -b feature/my-cool-feature
# or for bugfixes:
git checkout -b fix/fix-sync-issue
```

### Step 3: Set Up Your Local Environment
Depending on what area you want to work on:
- **Windows Desktop App**: Python 3.11+ (`pip install -r requirements.txt`)
  - Run app: `python windows/src/main.py`
- **Android App**: Android Studio (JDK 17+)
  - Open the `android/` directory in Android Studio.
- **Web UI & Backend**:
  - UI files live in `windows/src/ui/`.
  - Cloudflare Edge worker lives in `backend/worker.js`.

---

## Verify Your Changes (1-Click Quality System)

AANO includes an automated, **100% offline quality system** to keep the code clean and bug-free.

Before committing, run:
```bash
# On Windows:
quality.bat --quick

# On macOS/Linux:
python scripts/quality.py --quick
```

To run the complete verification suite (Python + Web + Security + Android unit tests):
```bash
quality.bat --check
```

*Note: You can install the automatic pre-commit hook anytime with `quality.bat --install-hook`. It will automatically check your work before each commit.*

---

## Commit & Submit a Pull Request (PR)

1. **Commit your changes**:
   ```bash
   git add .
   git commit -m "feat(ui): add new pastel theme"
   ```
2. **Push to your fork**:
   ```bash
   git push origin feature/my-cool-feature
   ```
3. **Open a Pull Request**:
   - Go to your fork on GitHub.
   - Click the **"Compare & pull request"** button.
   - Describe what your change does and why it helps.
   - Submit the PR! We will review it kindly and merge it into `main`.

---

## Code Guidelines & Standards

To ensure AANO remains high quality, reliable, and private:
1. **Zero Surveillance**: Never add trackers, analytics, telemetry SDKs, or third-party ads.
2. **Clean & Modular**: Keep code simple, readable, and well-structured. Avoid unnecessary heavy dependencies.
3. **Behavior-Preserving**: Make sure existing features (like Local Vault .zip backup, AANO Drop, and Google Drive Sync) continue working as expected.
4. **Be Respectful**: Treat fellow contributors with kindness, empathy, and respect.

---

## Need Help?
If you're stuck or have any questions, feel free to open a [GitHub Discussion](https://github.com/jamamjadalone/AANO/discussions) or ask in an [Issue](https://github.com/jamamjadalone/AANO/issues). We are happy to help!\n