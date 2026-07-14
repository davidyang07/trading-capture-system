@AGENTS.md

# Claude Code Instructions

## Starting a task

- Read the complete user request and relevant repository documentation before acting.
- Inspect existing implementation patterns before introducing new ones.
- Treat the sibling `tcs-reference` directory as strictly read-only.
- Keep investigation summaries concise and do not paste large source files into responses.
- Ask only questions that materially affect architecture or block progress.

## Initial planning session

- Begin in Plan mode.
- Perform read-only investigation of the active repository and `tcs-reference`.
- Do not modify files while still in Plan mode.
- Present the proposed architecture, source-of-truth selections, migration decisions, implementation phases, acceptance criteria, verification strategy, and blocking questions.
- Wait for explicit plan approval before implementation.

## Implementation workflow

- Follow the approved master plan one bounded phase at a time.
- Do not broaden the current phase without explaining why.
- Run targeted verification during implementation.
- Run phase-level verification before declaring completion.
- Update plans, status documents, ADRs, API contracts, and event contracts when behavior changes.
- Address root causes rather than hiding build or test failures.

## Claude configuration checkpoint

During the approved repository-foundation phase, create the complete project-specific Claude Code configuration:

- `.claude/settings.json`
- `.claude/rules/`
- `.claude/agents/`
- `.claude/skills/`

After creating and validating those files, stop before broad feature implementation and request a Claude Code restart so the new project configuration loads cleanly.

## Verification

Do not mark a phase complete without actual evidence.

Report:

- files changed,
- behavior implemented,
- architecture decisions,
- commands run,
- tests passed,
- tests not run,
- known limitations,
- documentation updated,
- next phase.
