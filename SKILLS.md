# AANO Skills Guide

This document explains the Claude Code skills available for the AANO project (Cross-platform notes app with Android + Windows versions).

---

## Available Skills

### `/lwh-plan-app` - Plan New Features
Turns an app idea into a written plan with:
- Feature scope definition
- Success criteria
- Technical blocks (security, data storage, UI)

**Example**: `/lwh-plan-app "Dark mode toggle for Windows settings"`

---

### `/lwh-challenge-plan` - Validate Plans
Stress-tests plans for holes and edge cases before implementation.

**Best for**: Validating major feature plans

---

### `/lwh-design` - Create Mockups
Builds UI mockups for new screens or layout changes.

**Best for**: UI design work in `pages/` or Windows CSS

---

### `/lwh-make-tasks` - Decompose Features
Splits plans into small, testable tasks with clear "Done when" criteria and verification methods.

**Best for**: Breaking down feature work into manageable pieces

---

### `/lwh-run` - Execute Tasks
Runs tasks with worker subagents, each completing before the next starts. Ideal for multi-file changes.

**Best for**: Complex tasks, batch changes, multi-step features

---

### `/lwh-stop` - Stop Running Tasks
Safely stops agent tasks at the nearest clean point.

**Best for**: Emergency stop of runaway processes

---

### `/lwh-security-check` - Security Audit
Scans codebase for vulnerabilities (critical, high, medium, low severity).

**Best for**: Pre-release security verification

---

### `/lwh-whats-next` - Project Status
Shows current project state: what's built, what's pending, next command.

**Best for**: Quick status check after changes

---

## Workflow for New Features

```
1. /lwh-plan-app "Feature description"
2. /lwh-challenge-plan              # Validate the plan
3. /lwh-make-tasks                   # Create task list
4. /lwh-run                          # Execute the work
```

---

## Workflow for Bug Fixes

```
1. /lwh-plan-app "Fix: <bug description>"
2. /lwh-make-tasks                   # Create small fix tasks
3. /lwh-run                          # Execute fixes
```

---

## Workflow for Security Audits

```
/lwh-security-check                   # Run security scan
# Review findings in docs/security-report.md
```

---

## Project-Specific Considerations

### Android Development
- All screens go in `android/pages/`
- Components in `android/components/`
- Data layer in `android/data/`
- File: Rule 21 - max 300 lines per file

### Windows Development
- Backend Python in `windows/src/`
- UI CSS in `windows/src/ui/css/`
- UI JS in `windows/src/ui/js/`
- PyWebView bridge API in `windows/src/api.py`

### Security Model
- Android: Keystore AES-256-GCM
- Windows: DPAPI encryption
- P2P Drop: AES-GCM transfer encryption
- Cloud Sync: OAuth with minimal scope

---

## Quick Start Examples

**Add a feature**:
```
/lwh-plan-app "Add reminder notifications"
/lwh-make-tasks
/lwh-run
```

**Fix a bug**:
```
/lwh-security-check
/llwh-run (if fixes needed)
```

**Check progress**:
```
/lwh-whats-next
```

---

## Related Files

- `AGENTS.md` - Agent Playbook & Rapid Engineering Directives
- `ARCHITECTURE_INDEX.md` - File map & structure
- `CLAUDE.md` - Project rules for AI agents
- `docs/tasks/index.md` - Current task list
- `python aano_dev.py check` - Health check
- `python aano_dev.py doctor` - Deep integrity check
- `python aano_dev.py stats` - Line statistics

---

## Installation

Skills are installed at `.claude/skills/` with symlinks to the lwh-ai-coding-skills repository.

To install: `npx skills add hassancs91/lwh-ai-coding-skills -a claude-code`

Or manually clone to: `.claude/skills/lwh/` and symlink skills to `.claude/skills/`