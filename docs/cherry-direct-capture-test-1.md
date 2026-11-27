# Direct Capture feasibility — Test Build 1

Base: OCR research checkpoint ce39807b2f9bf6998a0e0f817616d15ec1e63b1a. Separate branch and package. Production app unchanged. OCR remains available for later comparison.

Phase 1 deliberately tests what Viber and Messenger expose. User enables Accessibility in Android settings, explicitly starts a session, opens target chat, presses the visibly labeled capture button, and returns to the test app to inspect persistent snapshots. Stop in app or overlay. Process death disarms. No network, no automatic capture, no auto scrolling, no accessibility actions on other apps, no entry/ledger integration. Window-state events use package metadata only; root is read on button press only, target package checked twice. Overlay uses TYPE_ACCESSIBILITY_OVERLAY, not system-alert permission. All visible text/description candidates in the target tree are retained for manual review, excluding invisible/editable/password nodes. Do not test with private real chats unless authorized; use dummy messages. Clear deletes local snapshot rows. No export yet.

Capture count is successful snapshots, NOT messages. No green bubble markers, message parser, deduplication, identity claims, sender mapping or import until feasibility verified. Review is intentionally raw; duplicate parent/child nodes and UI labels may appear. Bounds are experimental. Java prototype avoids adding Compose/runtime dependencies; Kotlin integration can be designed later. Test source and CI verify isolated build/permissions; actual device behavior is unverified.

Device checklist:
- Enable service using normal Android user settings only, then start session. If Android refuses access, report restriction; do not bypass.
- Open Viber and Messenger test conversation with mixed Burmese/Latin digits, multi-line text, repeated identical messages, long text, and light/dark themes.
- Tap capture once; verify snapshot count increments only after successful SQLite insert. Scroll manually and capture again; compare raw rows with visible messages. Report whether text appears in node text or description and whether a long message is incomplete.
- Switch away; verify overlay disappears. Stop session; confirm no capture. Close/reopen test app and confirm snapshots survive. Clear and confirm count zero.
- No APK install over production. No real ledger writes. If overlay/root fails or content is missing, document before further implementation.

Later phases: verified bubble extraction and message identity/ambiguity, conservative overlap handling, status markers only when justified, durable inbox and review, volume and OCR comparison. No guarantee of complete or exact text until device tests.
