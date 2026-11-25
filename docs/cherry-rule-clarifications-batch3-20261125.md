# Missing rule clarification — batch 3 (partial)
Baseline Build 1946. Previous batch 2 requirements at 617d742716d3b75fc8fc01ce8ccc951f87f12b00. User's local numbering 1–9 corresponds to overall 21–29. No application code changes.

| No. | Canonical ID | User decision |
|---|---|---|
| 21 | new-mob-wheel | Use the လူမိုက်အုပ်စု group already in the reader as the trigger for 1267 wheel; one-week scope. Existing group membership 17,20,80,56,51; user identifies the group rather than listing members again. Confirm exact same-week boundary vs rolling week if needed. Earlier separate-group assumption superseded by user clarification. |
| 22 | new-bk-50 | Exclude from Better Data only. |
| 23 | new-special-9072 | Exclude from Better Data only. |
| 24 | new-special-3103 | Exclude from Better Data only. |
| 25 | new-bk-0chain | Exclude from Better Data only. |
| 26 | new-double-chain | Exclude from Better Data only. |
| 27 | new-bk-evening7 | User specifies one-week lifetime. Exact week boundary and stop-on-hit behavior not expressly stated. |
| 28 | new-session-match | Exclude from Better Data only. |
| 29 | new-day-mon | Both Monday AM and PM qualify; same-week lifetime. Last-tail-equality branch still lacks exact input definition. User message includes trailing unrelated/unclear text; no rule semantics inferred from that text. |
| 30 | new-day-tue | No answer supplied yet. |

Preserve all excluded entries in 2D Analysis and original source. Combined with batches 1 and 2: 17 retained in principle, 12 intentionally excluded, 8 remaining (30–37); retained items still require implementation, source scope and edge-case checks. No ranking, UI, source or archive changes. No claim of improved predictive accuracy.
