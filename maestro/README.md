# Assistant acceptance suite (Maestro)

End-to-end checks for the chat assistant, driven through the real UI on a device.
Complements the JVM unit tests: those pin the agent grammar and the gate, these
prove the wired-up app answers, labels, and refuses correctly with a model aboard.

## Run it

```bash
./gradlew :app:installDebug          # once; model file must already be on the device
./maestro/run_assistant_tests.sh
```

With an embeddings build, wait for the hybrid chip instead:

```bash
READY_LABEL="ready · hybrid" ./maestro/run_assistant_tests.sh
```

Maestro does not expand `${READY_LABEL}` inside command arguments, so the runner
stamps the label into run copies under `report/<timestamp>/flows/` and executes
those — the files under `flows/` stay templates.

Budget up to ~30 minutes: each agent turn waits up to 3 minutes for on-device
inference, and there are 10 questions across the three flows.

## What it checks

| Flow | Questions | Pass condition |
| ---- | --------- | -------------- |
| `01_portfolio` | worth, metals, margin, gold lookup | "from your account" label after each |
| `01_portfolio` | nameless "Find my holding" | fixed fallback, word for word |
| `02_docs` | deposit, brokerage, close account | "from the help documentation" after each |
| `02_docs` | password reset (uncovered) | fixed fallback, word for word |
| `03_honesty` | greeting | turn ends, no source label, no fallback |
| `03_honesty` | "Should I buy more stocks?" | instructed refusal ("can't advise…"), no fallback |
| `03_honesty` | send then Stop | no assertions — screenshot + logcat evidence |

Deliberately not asserted: exact figures and model wording, which legitimately
vary. Figures are verified from the screenshots and the tool-call logcat.

## Report

`maestro/report/<timestamp>/REPORT.md`: per-flow PASS/FAIL with durations, the
Maestro console logs, a screenshot taken after each flow, and a filtered logcat
(`LocalRagChat` / `LocalRagAgent` / `LocalRag`) showing every planned tool call,
observation size, and gate verdict.

To get a failure debugged, paste `REPORT.md` plus the failing flow's `.log` —
and the screenshots if any answer text looks wrong.
