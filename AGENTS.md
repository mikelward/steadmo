---
trigger: always_on
alwaysApply: true
last_modified: 2026-11-27
---

# Steadmo

An Android app (Kotlin, Compose) for someone with type 1 diabetes on CamAPS FX: one place
for glucose, trend and loop state, with quick ways into CamAPS FX to act on them. `SPEC.md`
holds the decisions (change one only with its reason), `TODO.md` the plan,
`docs/EXPLORATION.md` the integration research. Reading CamAPS FX's notification has
never run on a real phone.

## Fleet conventions

Shared by every mikelward repo and the user-level agent config; canonical in mikelward/conf
`agents/AGENTS.md`. A repo's Project section overrides it.

### This file

- **Session start**. At the start of every session, print the full absolute path of the
  `AGENTS.md` you loaded and its `last_modified` date.
- **Canonical**. AGENTS.md is canonical; `CLAUDE.md` and `GEMINI.md` point to it. Keep it
  brief, generic to all agents (noting per-agent exceptions), and under 22,000 bytes.
  Bump `last_modified` whenever you edit this file.

### Talking to the user

- **Questions**. One at a time; wait for the answer.
- **Messages**. Send one only for a question, a blocker, something to act on, or a change
  of plan. No progress reports or narrating events.
- **Claude Code**. It reliably shows only a turn's last message; put everything there.
- **Replies**. Short, key point first, ending with open PR links and any pending
  question.
- **Commands and URLs**. Give ones that work as pasted, never placeholders: find each
  value in the repo first, and ask only for those it lacks.
- **Network access**. If you want access to a host and you can't access it or a mirror,
  ask the user to allowlist the domain(s) along with the purpose.

### Git

- **Branches**. `<agent>/<topic>` off a fresh `origin/main` (fetch by refspec). Never
  commit to `main`.
- **Your branches**. Push, force-push with lease, and delete them freely. Ask before
  touching others', `main`, settings, secrets or paid services.
- **Commits**. One per logical change; fold in review fixes.
- **Subjects**. Changelog lines: imperative, the user-visible change, ≤ 70 chars. PR
  titles match.
- **Prefixes**. `docs:`, `test:`, `build:`, `refactor:` mark what users won't notice. No
  `feat:` or `fix:`.
- **After a merge**. Branch afresh.
- **Shallow clones**. Unshallow before trusting history or calculating a version code.
- **Language**. US English everywhere.

### Pull requests

- **Opening**. Open the PR unasked; update its title and body before each push.
- **Descriptions**. Scope, rationale and test plan, plus the change's privacy,
  reliability and cost impact (say "none" where there is none).
- **TODO.md**. Tick an item off in the PR that implements it, not a follow-up. Don't
  spend a CI run on trivia.
- **Watching**. PR events plus one scheduled check (events miss CI success and Codex's
  👍).
- **Auto-merge**. Enable it on PRs that target `main`; stack dependent ones.
- **Base moved**. After a merge, tell open PRs on that base whose body ends with a session
  URL.
- **Merge or close**. Answer remaining threads and stop watching.

### Reviews

- **Codex**. It reviews automatically. Fix, decline with a reason, or defer to `TODO.md`
  every comment; reply citing the commit, then resolve.
- **Merit**. Codex over-reads rules, privacy especially: quote and decline rather than
  cripple the code. Design changes are the user's call.
- **Verdict**. 👍 is clean, 👀 is reviewing. Only the code review gates merging: its
  findings (review comments, PR comments, reviews) block it. Security reviews often
  hang; don't wait on them.
- **Hung review**. `@codex review` after five quiet minutes, again an hour later, then
  tell the user.
- **After a 👍**. Ask before unneeded changes.
- **Reports**. Give each review's findings in your next message.

### Autonomy

- **Fix proactively**. Lint, test, presubmit, CI and review failures, unasked.
- **Proactive refactors**. Repeated bugs in one area, duplicated code, or a change with
  twins elsewhere: suggest the redesigned or shared version.
- **Drive to merge**. Once the details are agreed, see the change through unattended:
  implement it, open the PR, keep it mergeable (fix CI, answer reviews, rebase when it
  conflicts or falls behind), and merge once CI is green and Codex 👍s, or let
  auto-merge.
- **Drive**. Take `TODO.md` items one at a time and drive each to merge, until done or
  stopped.
- **Autopilot**. Drive without asking, logging guesses in `TODO.md` under *Decisions
  needing review*. Still ask before anything irreversible, outside this repo, or private.
- **Red baseline**. Fix existing failures first, in their own commit.
- **Fix fully**. Fix a reported problem's cause, everywhere it occurs (every platform,
  consumer and copy), not only the case reported.
- **Lasting fixes**. Fix the cause in a repo. Don't propose a step that lasts only this
  session (stopping a service, a setting in this container) or a hand edit to a dotfile:
  neither persists.

### Quality

- **Tests**. Every behavior change gets one; a bug fix's fails first. Assert behavior,
  not structure. No sleeps or retries; never disable a failing check.
- **Docs**. Keep `SPEC.md` and `README.md` current.
- **CI**. Wait for it before claiming success.
- **Errors**. Never swallow one silently: every catch logs it with context (no user
  data), cleans up what the `try` acquired, and handles the case explicitly. To ignore
  one on purpose, say why in a comment and still log it at debug level.
- **Logs**. Every process logs that it started, that it ended and how, and each error,
  where that outlasts it, a logout and a reboot: the journal, or a file under
  `~/.local/state`. Never only `/tmp`, a runtime directory or `/dev/null`.
- **Cost**. State the cost and reliability of new dependencies or services up front.
- **Privacy**. No user data (personal paths, hostnames, tokens, keys, private remotes)
  leaves this machine; use placeholders.
- **Portability**. Flag anything that won't run on Linux and macOS.
- **UI text**. Words a person would say, as few as do the job. Say what a setting does;
  leave out what works by default, what the screen already shows, and how it works.
- **Terminology**. Standard domain terms, never coined ones: "Text console layout is
  Dvorak", not "The text consoles type Dvorak"; "dimming inactive windows", not "the dim".

## Project

### Principles, in order

1. Never dose. Bolus, Boost and Ease-off are set and confirmed in CamAPS FX; Steadmo only
   opens it, and never automates a tap inside it.
2. Never show stale data as current: every value carries its age, and old ones grey out
   or disappear.
3. Never fail silently: when CamAPS FX stops updating, say so.
4. Glanceable first: the value, the arrow and the age are readable at arm's length.

### Rules

- **Logic**. Parsing, trend, and alert rules live in the plain-Kotlin `:core` module and
  are tested against captured notification text. Android code stays thin.
- **Privacy**. Glucose data never leaves the device. Anything new leaving it is a
  product decision.
- **Medical safety**. Steadmo is a companion display, not a medical device. CamAPS FX
  and Dexcom alarms stay authoritative; never suggest turning them off.
- **Play policy questions**. Permissions (notification access especially), foreground
  services and Data Safety go to the user with the policy text, even on autopilot.
- **Build**. `./gradlew test` and `./gradlew lint`; `.claude/hooks/session-start.sh`
  sets up the SDK. Notification access and CamAPS FX need a real phone: say what you
  couldn't verify.
- **Tests**. `./gradlew test koverVerify` keeps line coverage at 95% or more.
- **Screenshots**. `./gradlew test -Proborazzi.test.record=true` re-records
  `app/src/test/snapshots/images`; commit the PNGs with the change. CI re-records and fails
  on any difference. Light and dark for every screen.
- **UI**. 4dp grid, short copy, standard Material icons. Trend arrows use the seven
  standard CGM arrows (`SPEC.md` §4).
- **Logging**. Through `SteadmoLog` (mikelward/androidlog), never `android.util.Log`:
  a format string plus arguments, one line per decision, with the reason. Every catch logs
  with `failure(e, ...)`.
- **Units**. mmol/L first; mg/dL is converted on the way in.
- **Translations**. Wait until the maintainer approves the English.
