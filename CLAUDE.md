# CLAUDE.md - AANO Project Rules

[link: https://github.com/hassancs91/lwh-ai-coding-skills]

## Rules every change follows

- **Rule 21 Line Cap**: Never create files over 300 lines. Decompose if approaching 280.
- **Token-Saver Protocol**: Read ARCHITECTURE_INDEX.md first to find target file.
- **No Duplicate UI**: Check `index.html` and layout XMLs first. Do not create duplicate toggles or buttons if one already exists.
- **Sync Architecture Index**: If you add any new file (like `api_theme.py`), always run `python aano_dev.py map`.
- **Anti-Slop Guard**: Never write empty catch blocks. Always log exceptions.
- **Zero Trademark Footprint**: Never mention commercial brand names in code.

## Project Structure (from ARCHITECTURE_INDEX.md)

- **android/pages/**: Screen activities and fragments (max 297 lines)
- **android/components/editor/**: Rich text editor delegates (~100-300 lines each)
- **android/components/dialogs/**: Modal dialogs (max 264 lines)
- **android/componenets/views/**: Custom views (max 211 lines)
- **android/components/adapters/**: RecyclerView adapters
- **android/data/**: Storage, cipher, models, repositories
- **windows/src/**: Python backend & JS frontend
- **windows/src/ui/css/**: Stylesheets (max 298 lines)
- **windows/src/ui/js/**: JavaScript modules (max 274 lines)

## Security

- Always consult docs/audit/SECURITY_AUDIT.md before changes
- Run `python aano_dev.py doctor` before committing
- No secrets in commits (check `.gitignore` for patterns)

## Working Directory

Project root is `C:\Users\AS\Desktop\App or web\AANO`.
Use forward slashes in commands when possible.

---

## Current lwh Project Status

Plan: See SKILLS.md for lwh skill documentation  
Tasks: `docs/tasks/index.md` - Dark mode toggle feature (T00-T07 ready)  
Status: Tasks ready to execute  
Next command: `/lwh-run` to begin dark mode implementation