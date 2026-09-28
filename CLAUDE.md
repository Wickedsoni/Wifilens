## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).

## Engineering rules (read first)

- `docs/engineering/ANDROID_GUIDELINES.md`: architecture, Hilt, coroutines, permissions, M3 Expressive, motion, naming, testing, performance, release checklist. It's distilled from Google's reference repos, so don't re-study them.
- `docs/project/ROADMAP.md` (the 2.0 sprint plan) and `docs/project/sprint-log.md` (status, DoD, token log).
- Device testing happens on the user's physical **Moto Edge 40**. Stop and ask before any device step. adb: `C:/Users/Avik/AppData/Local/Android/Sdk/platform-tools/adb.exe`.
