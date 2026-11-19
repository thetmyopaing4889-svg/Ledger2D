# SET snapshot acquisition reliability — from successful Build 1804

## Evidence driving the fix
- Google Colab's Python HTMLParser extracted SET Index, Value and Last Update during its tested watch sequence.
- Android one-shot probe on phone did fetch/parse SET HTML (HTTP 200). The Nov 19 morning LIVE log contained only a post-cutoff snapshot (received 12:01:57 Yangon, SET source updated 12:01:44, phase WAITING).
- The previous app did not record failed requests or parser stages; absence of earlier unique snapshots does NOT prove no GET requests were made.
- No exact root cause for 11:59–12:01 was established by the existing data.

## Changes
1. Port Colab's visible-text HTML extraction semantics to jsoup (real HTML5 tokenizer and entity decoder). Strip script/style/noscript/template and bind Index, Value and Last Update strictly inside the SET Index Series panel, never SET50. Preserve exact 2D digit calculation.
2. Bounded 3MB HTTP GET, explicit disable Android URL caching, request no-cache revalidation, 10s connect / 12s read bounds, tolerate unexpected Content-Type when actual HTML is parseable. Collect Date/Age/Cache-Control and HTTP status. No attempts to evade 403/429/access controls.
3. Every 11:59–12:04 SET request now generates REQUEST_START and REQUEST_END evidence with a success/failure/parser stage and source time. In addition, keep distinct SNAPSHOT and selected UI_RESULT / LUKE_COMPARE.
4. Source timestamp conflicts quarantine only that exact stamp; newer pre-cutoff observations can recover. WAITING and CONFLICT do not prematurely stop bounded observation, but a post-cutoff value is never promoted retroactively into a result.
5. Stop after valid UI_RESULT_READY or forbidden/limited HTTP status. HTTP 5xx backs off to 15s; successful observations retain the existing 3s scheduled cadence (subject to network duration).
6. Debug-only SET Snapshot Test screen displays cache/parser metadata and allows copying the audit to clipboard. Release app's UI keeps only the normal Cherry launcher.

## Invariants
- SET is for UI display and separate private diagnostics only. Luke result_1200 / result_430 alone control official final, Room/history and winner auto-save.
- Do not edit main or non-SET feature source (Research, Rules Library, Better Data, Formula Engine, Customer, Agent, Ledger/Financial).
- Do not claim the Nov 19 root cause is resolved until phone tests demonstrate source updates before 12:01 and valid handover.
- New offline tests cover nested HTML/entities, blocked data, failure stages, 429, cache headers and ambiguous timestamps.
- HTTP polling permission/rate limits remain a separate external constraint. Respect the site and stop on 401/403/429.
