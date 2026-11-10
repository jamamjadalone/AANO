# Antigravity Workspace Rules & Directives (AS Studio)

## 1. Communication & Tone
- **Language**: Hamesha **Hinglish** (Urdu in Roman script) ya **English** mein communicate karo. Chat responses mein **Urdu script (عربی/اردو رسم الخط) likhna strictly prohibited hai** — hamesha Roman letters mein likho.
- **Tone**: Direct, honest, concise, aur practical. Faltu ki lambi chori batein ya "trust me it works" bolna strictly prohibited hai.

## 2. Memory & Workspace First (Strict Directive)
- **Check Past Memory & Local Files First**: Jab user kisi setup ya task ka zikr kare (jaise Oracle, scripts, keys, etc.), toh hawa mein ya generic internet answers dene se pehle **workspace files aur past memory search karo**. Bina check kiye assume karna ya generic baatein bolna mana hai.

## 3. Execution Discipline: Plan & TODO First (Universal Rule)
- **Universal Application**: Chahe Stock assets hon, coding/software development ho, scripts hon, ya cloud automation — yeh rule har kaam par apply hoga.
- **No Random Execution**: Bina plan aur TODO list ke seedha code likhna ya tools fire karna strictly mana hai.
- **Workflow**:
  1. **Phase 1: Clear Plan & Checklist**: Pehle task ko break down karo aur short TODO list banao (Step 1, Step 2, Step 3).
  2. **Phase 2: Review/Alignment**: Steps clear karo.
  3. **Phase 3: Focused Execution**: Ek waqt par focus karke execute karo aur checklist update karte jao.
  4. **Phase 4: Empirical Proof**: Result ko verify karo (tests, file exist check, clean logs) tabhi complete mark karo.

## 4. General Development & Coding Best Practices (All Projects)
- **Modular & Clean Architecture**: Code hamesha clean, self-explanatory, error-handled aur reusable banao.
- **Targeted Edits Only**: Poori poori 1000-line files baar baar replace ya rewrite mat karo; sirf zaroori block/function ko modify karo.
- **Zero Hallucination Imports**: Kabhi aisi library import mat karo jo verify na ki ho. Dependencies pehle check karo.

## 5. Token & Search Optimization Protocol (Smart Dynamic Search)
- **Normal Tasks**: Normal prompts mein quota bachao — zero spam searches, maximum 1 targeted search agar zaroori ho.
- **Deep Research Override**: Jab user explicitly bole **"deep research"** ya **"research deeply"**, tab agent fully free hai jitni zaroori searches ho sakein kare taake accurate, thorough aur reliable data mile.

## 6. Hardware & Safety Guardrails (Zero Local Load)
- **Local Specs**: User PC par Core i7-3540M aur 8GB RAM hai. 
- **Rule**: Local PC par heavy tasks (AI image generation, upscaling, heavy video/rendering) chalana strictly mana hai.
- **Cloud Heavy Lifting**: Saara heavy AI/compute workload Kaggle GPU, Google Drive, ya cloud automation ke zariye hoga.

## 7. Vector & Stock Assets Quality Standards
- **Subject**: Isolated single subjects only (e.g., kawaii animal mascot, clean icon). No furniture, no laptops, no complex multi-object scenes.
- **Cleanliness**: 100% textless (koi AI fake words/letters nahi hone chahiye). Pure solid white background.
- **Pipeline Order**: FLUX generation -> background isolation (rembg) -> 4x upscale (Real-ESRGAN) -> vtracer clean SVG -> Inkscape EPS.
- **Licensing**: Only Apache 2.0 / BSD / MIT commercial-safe models (FLUX.1-schnell). Non-commercial models strictly prohibited.

## 8. Zero Image Dependency & Media Payload Guardrail (API Crash Prevention)
- **Zero Image Loading / Screencap Prohibited**: Agent kabhi bhi image files (`.png`, `.jpg`, `.jpeg`, `.webp`, `.bmp`, `.gif`) par `view_file` call nahi karega aur na hi `adb screencap` capture karke inspect karega. Image payload se Google API mein `HTTP 400 Bad Request INVALID_MEDIA (error 2011)` crash trigger hota hai jo conversation session ko permanently corrupt/block kar deta hai.
- **Pure Text-Based & Empirical Verification**: Verification hamesha 100% text logs aur direct terminal commands ke zariye hogi (e.g., Gradle compilation logs `:app:assembleRelease --offline`, ADB dumpsys/logcat, file existence, unit test output). UI testing ke liye screencaps par depend hone ke bajaye direct code, layouts aur build status par kaam hoga.
- **Direct Phase Execution**: Image checks ke bina direct next phases aur tasks execute karo.

## 9. Preservation of Standalone Backend & Assets (Strict Guardrail)
- **Zero Accidental Deletions**: `backend/` directory (`worker.js`, `schema.sql`) aur standalone authentication code (Cloudflare Worker + Google Apps Script Mail Bridge) ko codebase aur GitHub repository se kabhi bhi delete ya strip nahi karna. Yeh AANO ka intentional future feature hai (password-less Google bypass / shared device access).

## 10. Centralized Version Management & Release Guardrail (Strict Protocol)
- **Single Source of Truth**: Release versions ko kabhi bhi multiple files mein manually hardcode ya scatter nahi karna. Hamesha `python bump_version.py <version>` use karo jo atomicity ke sath `VERSION`, Android `build.gradle.kts` (`versionCode` + `versionName`), Windows `aano.iss`, `docs/index.html`, aur `CHANGELOG.md` ko synchronize karta hai.
- **Version Integrity Audit**: Kisi bhi build ya release se pehle `python bump_version.py --check` run karke verify karo ke Android, Windows aur Web sab exact same version par synced hain.

## 11. Permanent UI & Interface Lock (Islamic Azkar Pro)
- **Zero Redesign Directive**: Islamic Azkar Pro ka visual design, layout aur core UI architecture 100% PERMANENTLY LOCKED hai. Koi bhi AI agent ya developer is interface ko alter, shuffle, rewrite ya redesign nahi karega.
- **Strictly Frozen Components**:
  1. Top Bar: Streak badge (`🔥 X دن`), direct sound toggle (`🔔`/`🔇`), glassmorphic zikr selector pill (`[ ☀️ ذکر منتخب کریں ▾ ]`), aur three-dot settings button (`⋮`).
  2. Borderless Dynamic Greeting Tagline: Pure spiritual text without explicit digits or prayer hours.
  3. Luxury Reading Counter: Counter ring with haptic animation, centered clean typography, and dua cards.
  4. 7-Card Welcome Landing Dashboard with Salam and direct guidance.
  5. Completion Screen with "↺ دوبارہ شروع کریں" and "🏠 مرکزی صفحہ".
  6. Unified Settings Modal with card-based sections (Zoom, Quran font, Audio, Themes, Kahf preferences) and footer ("محفوظ کریں ✓" aur "📲 ایپ انسٹال کریں").
- **Only Allowed Future Changes**:
  1. Adding new Azkar / Supplications data into the catalog or custom list.
  2. Adding new setting options / toggles inside the existing three-dot settings modal.
  3. Bug fixes, performance optimizations, and offline PWA cache enhancements.

## 12. Sacred Reference Adab & Human Error Disclaimer Protocol (Permanent Lock)
- **Cautious Hadith Attribution**: Kisi bhi Hadith ka hawala dete waqt direct/absolute daawa (jaise "رسول اللہ ﷺ نے فرمایا:" ya "نبی کریم نے فرمایا:") likhna strictly mana hai. Hamesha ehtiyat aur adab ke sath **"روایت کے مفہوم کے مطابق..."**, **"حدیث شریف کا مفہوم ہے کہ..."**, ya English mein **"According to the contextual meaning of the narration (Hadith)..."** likha jaye.
- **Noble Quranic Quotation**: Quranic ayats aur hawala ke liye hamesha ba-adab aur mohtaram alfaaz istemal hon: **"ارشادِ باری تعالیٰ ہے — [سورۃ ...: آیت ...]"**, **"فرمانِ حق تعالیٰ ہے..."**, ya English mein **"As declared by Allah the Almighty in the Holy Quran — [Surah ...: Verse ...]"**.
- **Human Error & Tas-heeh Disclaimer**: Settings modal aur documentation mein humble disclaimer hamesha active rahay: *"تمام تراجم، اعراب اور حوالاجات میں انتہائی احتیاط برتی گئی ہے، تاہم انسان خطا کا پتلا ہے۔ اگر کسی بھی مقام پر تصحیح یا اصلاح درکار ہو تو برائے مہربانی مطلع فرمائیں۔"* (Tamam 6 languages mein).

## 13. Zero Third-Party Brand & Trademark Footprint Protocol (Strict Store Safety)
- **Functional Reference Only**: Jab user kisi feature, flow ya UI logic ke liye kisi commercial app ya third-party product ka hawala de (jaise Zapya, SHAREit, WhatsApp, Truecaller, Keep, etc.), toh use **sirf aur sirf architectural / conceptual reference** samjha jaye.
- **Zero Trademark in Codebase**: Kisi bhi commercial app, company ya competitor ka trademark/brand name **KABHI BHI** code, UI text, button labels, toasts/dialogs, layout comments, code variables, documentation, ya git commit messages mein hardcode/mention **NAHI** hoga. Is se Google Play Store aur app marketplaces par "Impersonation / Trademark Infringement" policy strikes lagne ka khatra hota hai.
- **Mandatory Native Terminology**: Hamesha generic, original, professional aur descriptive technical words use hon (e.g. "Direct Hotspot Mode", "Offline P2P Transfer", "Instant Sync", "Encrypted Vault").

## 14. Centralized Cloud Rule Backup Protocol (Google Drive Antigravity Sync)
- **Continuous Cloud Parity**: Jab bhi koi naya rule, directive ya policy add ya modify ho, toh `GEMINI.md` ko foran Google Drive ke `Antigravity/` folder mein `Antigravity_Rules_Master.txt` ke naam se upload/sync kiya jaye.
- **Future Guarantee**: Kisi bhi future session mein rules ki continuity mehfooz rahe aur machine change/reset hone par bhi rules cloud se instant restore ho sakein.

## 15. Strict Google Drive OAuth Scope Guardrail (Permanent Zero Scope-Creep Policy)
- **Strict Single Scope**: Google Drive integration mein sirf aur sirf `DriveScopes.DRIVE_FILE` (`https://www.googleapis.com/auth/drive.file`) allowed hai.
- **Strict Prohibition for AI & Contributors**: Koi bhi AI agent ya human contributor kabhi bhi broader, sensitive, ya unverified scopes (jaise `DriveScopes.DRIVE`, `DRIVE_APPDATA`, `drive.full`, etc.) code, manifests, ya PRs mein introduce ya propose **NAHI** karega.
- **Zero App-Verification Risk**: Sensitive/restricted scopes Google OAuth warning screens (*"Google hasn't verified this app"*) trigger karte hain, app verification review demand karte hain, aur user trust destroy karte hain. `DRIVE_FILE` app-created files tak bounded rehta hai, frictionless sign-in deta hai, aur user privacy 100% guarantee karta hai.

## 16. Strict Research & Inspection First Protocol (Zero Assumptions Guardrail)
- **Zero Casual / Memory Assumptions**: Kisi bhi software, module, tool, framework, ya package (jaise HMA-OSS, Zygisk Next, TrickyStore, Magisk modules, etc.) ke architecture ya backend working mechanism par hawa mein assumption lagana ya purane generic knowledge par depend karna (maslan Zygisk module ko Xposed/LSPosed se mix karna) strictly PROHIBITED hai.
- **Mandatory Direct Inspection Before Explaining**: Kisi bhi component ke baare mein user ko guide karne, solution propose karne, ya instructions dene se pehle uske files, `module.prop`, configuration, manifest aur binaries ko system par `view_file`, `cat`, ya terminal tools se inspect karna lazmi hai.
- **Root Cause & Verification**: Jab bhi koi system feature (jaise app hiding, attestation, hook injection) discuss ho, pehle device ka live state aur module type verify karo ke woh standalone Zygisk hai, Xposed module hai, ya kernel/su level tool hai.

## 17. Zero Unsolicited PII & Personal Data Guardrail (Strict Privacy Directive)
- **Zero Assumption on PII**: User ki zaati details — jaise email address, physical location / city / country, phone number, real name, ya personal contact links — ko KABHI BHI kisi code, README, profile, config, commit message, ya documentation mein khud se assume karke add ya expose NAHI karna.
- **Explicit Instruction Only**: Personal identity ya location details sirf aur sirf tabhi add hon gi jab user EXPLICITLY aur LAZMI taur par kahe ke "mera yeh email ya yeh city/location add karo". Warna default mein hamesha neutral, generic, ya purely professional placeholders / public usernames use kiye jayen.
- **Privacy First Across All Projects**: Yeh rule har repository, profile README, documentation, metadata, aur code file par uniformly apply hoga without exception.

## 18. Zero Hardcoded Drive & Local Paths Guardrail (Portability & Anti-Pollution Directive)
- **Zero Hardcoded Local Paths**: Kisi bhi project, script, automation ya tool mein local drive letters ya machine-specific hardcoded paths (jaise `C:\Users\AS\...`, `D:\...`, `E:\...`) hardcode karna **STRICTLY PROHIBITED** hai.
- **Dynamic & Relative Path Architecture**: Agar kisi script ya module ko files ya folders read/write karne ki zaroorat ho, toh hamesha **relative paths** ya **dynamic runtime resolution** use hogi:
  - Python: `Path(__file__).resolve().parent` ya `os.getcwd()`
  - Node.js: `__dirname` ya `process.cwd()`
  - Bash: `$(cd "$(dirname "$0")" && pwd)`
- **Zero Random Folder Creation**: Kisi bhi drive (C:, D:, etc.) ke root ya random directory mein bina explicit user instruction ke koi file ya folder create karna strictly mana hai. Saara kaam sirf aur sirf current workspace repository ya scoped output directory ke andar hoga.
- **Config / Environment Variables for External Paths**: Agar kisi project ko external drive ya cloud sync path ki lazmi zaroorat ho, toh use hardcode karne ke bajaye hamesha `.env`, user prompt, ya command-line argument (`--output-dir`) ke zariye dynamically accept kiya jaye.

## 19. Andrej Karpathy Engineering Guardrails (Zero Wasted Cycles & Anti-Hallucination)
- **1. Think Before Coding (Never Assume, Never Hide Confusion)**:
  - Kabhi bhi hawa mein ya assumptions par code mat badlo. Agar requirement ambiguous ho ya DOM/flow uncertain ho, seedha pehle inspect karo aur explicitly clarify karo.
  - Blind trial-and-error changes commit karna strictly prohibited hai. Har change se pehle actual cause samjho.
- **2. Simplicity First (Minimum Code That Solves The Problem)**:
  - Jo pucha gaya ho sirf wahi implement karo — koi speculative feature ya unnecessary complex abstraction nahi banani.
  - Har code piece ko senior-engineer lens se evaluate karo: "Agar yeh 15 lines mein ho sakta hai toh 100 lines ka bulky logic kyun likhna?" Overcomplication zero tolerance policy hai.
- **3. Surgical Changes (Touch Only What You Must)**:
  - Sirf wahi lines aur functions touch karo jo directly problem solve karne ke liye zaroori hon.
  - Working code, adjacent functions, comments ya formatting ko bina wajah "improve" ya alter mat karo. Har changed line direct user request se trace honi chahiye.
  - Kabhi broad ya global selectors na likho jo scope se bahar ke elements ko hijack kar lein (e.g. background elements affecting active views).
- **4. Goal-Driven Execution & Loop Until Verified**:
  - Har task ka verifiable success criteria define karo:
    1. Step -> verify check
    2. Step -> empirical evidence
  - Empirical proof ke bina task complete mark mat karo. Weak criteria par hawa mein aagay barhna mana hai.

## 20. CI/CD & Remote Action Triage Protocol (Zero Local Detour Directive)
- **Zero Local Fallback Assumption**: Jab user kisi CI/CD workflow, GitHub Actions, cloud build, ya remote automation ke failure ya error ka zikr kare, toh **local machine par switch karke local build ya tests chalana STRICTLY PROHIBITED** hai. Local machine par shift hone se na runner ka issue solve hota hai aur na hi user ka waqt bachta hai.
- **Mandatory Remote Log Inspection First**: Agent ka sab se pehla aur fori qadam remote runner/job ke exact failure logs inspect karna hoga (`gh run view --log-failed` ya direct workflow job logs) taake exact remote failure point aur runner context pata chal sake.
- **Surgical Remote/Environment Diagnosis**: Failure ka root cause remote runner context (jaise network timeouts, missing runner secrets, Ubuntu/runner toolchain version, caching, ProGuard, ya workflow YAML steps) ke mutabiq identify aur fix kiya jayega.
- **Loop Until Remote Verified**: Fix commit aur push karne ke baad verification hamesha remote CI workflow (`gh run list` / `gh run view`) ke live passing status (`conclusion: success`) se confirm hogi.

## 21. Beginner-Friendly Architecture & Modular Organization Protocol
- **Keep Organized for Beginners**: Codebase structure clear aur readable hona chahiye taake beginner bhi asaani se navigate kar sake.
- **Directory Roles**:
  - Screens go in `pages` (or `ui/pages`).
  - Shared pieces / reusable UI elements go in `components`.
  - Saving, loading, persistence, and storage logic goes in `data`.
- **Single Responsibility Principle**: One job per file. Monolithic files strictly prohibited.
- **Strict File Size Cap**: Maximum 300 lines per file (`No file over 300 lines`). Agar file 300 lines cross kare toh use focused modular sub-components ya helper files mein split karo.
- **Check Existing First**: Before writing anything new, check if it already exists to prevent duplication and bloat.
- **Folder-by-Folder Safe Refactoring**: Cleanup one folder at a time, strictly verifying that behavior does not break and without changing what the app does.
