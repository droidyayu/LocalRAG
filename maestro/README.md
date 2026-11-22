# Assistant acceptance suite (Maestro)

End-to-end checks for the chat assistant, driven through the real UI on
Android and on the iOS simulator. Complements the JVM unit tests: those pin
the agent grammar and the gate, these prove the wired-up app answers, labels,
and refuses correctly on both platforms.

## Lanes

| Lane | Model | When |
| ---- | ----- | ---- |
| `scripted` | scripted engine (launch argument `localrag.engine`), real bundle / retrieval / tools / gate / UI | every change; deterministic |
| `model` | real Gemma model aboard the device | by hand / nightly; slower, can flake |

## Layout

- `flows/common/` — shared subflows used with `runFlow` + `env`: launch with
  the scripted engine, open the Assistant tab, ask one question, clear chat.
- `flows/scripted/` — deterministic lane, one file per flow.
- `flows/model/` — real-model lane (today's 01–03 suite, unchanged apart from
  the keyboard command).
- `lib/android.sh`, `lib/ios.sh` — platform layer sourced by the runner.
- `run_acceptance.sh` — the runner. `run_assistant_tests.sh` is a thin
  wrapper (`--platform android --lane model`) so existing instructions work.

## Run it

Android (debug APK installed; model file already on the device for the
model lane):

```bash
./gradlew :androidApp:installDebug
./maestro/run_acceptance.sh --platform android --lane scripted
./maestro/run_acceptance.sh --platform android --lane model
```

iOS simulator (`.app` built; exactly one simulator booted):

```bash
./maestro/run_acceptance.sh --platform ios --lane scripted
IOS_APP_PATH=<path to iosApp.app> ./maestro/run_acceptance.sh --platform ios --lane model
```

One flow only: append `--flow 20_portfolio`. With an embeddings build, wait
for the hybrid chip in the model lane:

```bash
READY_LABEL="ready · hybrid" ./maestro/run_acceptance.sh --platform android --lane model
```

`READY_LABEL` only affects the model lane. Maestro does not expand
`${READY_LABEL}` inside command arguments, so the runner stamps the label
into run copies under `report/<run>/flows/` and executes those — the files
under `flows/` stay templates. The scripted lane always waits for
`ready · bm25` and needs no stamping.

Push the model file: `scripts/push_model.sh [e2b|270m] [adb-serial]` on
Android; `scripts/push_model_ios.sh [e2b|270m] [simulator|device <udid>]`
on iOS (looks in `./` then `$MODEL_DIR`; the app must have been installed
once so its container exists).

Budget up to ~30 minutes for the model lane: each agent turn waits up to
3 minutes for on-device inference. The scripted lane answers instantly.

## What it checks

| Flow | Questions | Pass condition |
| ---- | --------- | -------------- |
| `scripted/00_boot` | boot | `ready · bm25` chip; Retrieval tab shows `53 chunks`, `content v1` |
| `scripted/10_retrieval` | 5 doc searches | top-1 chunk id per query (`deposit-funds`, `brokerage-charges`, `close-account`, `gold-storage`, `overnight-charges`) |
| `scripted/20_portfolio` | worth, metals, margin, gold lookup | `from your account` after each |
| `scripted/20_portfolio` | nameless "Find my holding" | fixed fallback, word for word |
| `scripted/21_docs` | deposit, brokerage, close account | `from the help documentation` after each |
| `scripted/21_docs` | password reset (uncovered) | fixed fallback, word for word |
| `scripted/22_honesty` | greeting | turn ends, no source label, no fallback |
| `scripted/22_honesty` | "Should I buy more stocks?" | refusal (`can't advise`), no fallback |
| `scripted/23_cancel` | Stop mid-turn (slow engine), ask again | second answer arrives with `from your account` |
| `scripted/24_followup` | worth, then "and the profit?" | second turn ends, no crash |
| `scripted/31_relaunch` | stop + relaunch without clearState | `ready · bm25` again, app usable |
| `scripted/40_no_model` | fresh state, no model file | `Model not found` banner; docs search still works |
| `model/01_portfolio` | worth, metals, margin, gold lookup | `from your account` after each |
| `model/01_portfolio` | nameless "Find my holding" | fixed fallback, word for word |
| `model/02_docs` | deposit, brokerage, close account | `from the help documentation` after each |
| `model/02_docs` | password reset (uncovered) | fixed fallback, word for word |
| `model/03_honesty` | greeting | turn ends, no source label, no fallback |
| `model/03_honesty` | "Should I buy more stocks?" | instructed refusal (`can't advise…`), no fallback |
| `model/03_honesty` | send then Stop | no assertions — screenshot + log evidence |

Deliberately not asserted: exact figures and model wording, which legitimately
vary. Figures are verified from the screenshots and the tool-call log.

## Report

`maestro/report/<timestamp>-<platform>-<lane>/REPORT.md`: per-flow
PASS/FAIL with durations, the Maestro console logs, a screenshot taken
after each flow, and a filtered device log (`LocalRagChat` /
`LocalRagAgent` / `LocalRag` on Android; the `iosApp` unified-log slice on
iOS). The runner exits non-zero on any failed flow, so CI can gate on it.

To get a failure debugged, paste `REPORT.md` plus the failing flow's `.log` —
and the screenshots if any answer text looks wrong.

## iOS notes

- Maestro drives the iOS **simulator** only. The runner passes
  `--platform ios --udid <booted udid>` (`xcrun simctl list devices booted -j`)
  and needs exactly one booted simulator.
- Tapping and reading by visible text and placeholder works on the Compose
  iOS UI; flows use `hideKeyboard` (there is no back key).
- Reinstalling or `clearState` wipes the model file (the container path
  changes); only `40_no_model` runs with a fresh state.
- Kotlin `println` output may not reach the unified log, so the iOS
  device log can be thinner than Android's logcat — the report says so
  when that happens.
- The simulator has no GPU delegate path of a physical iPhone: use the 270M
  model there and keep E2B for devices and Android.

## Several Android devices attached

The runner needs one target. With more than one adb device (say a phone and an emulator), pick one:

```bash
ANDROID_SERIAL=emulator-5554 ./maestro/run_acceptance.sh --platform android --lane scripted
```

Maestro cannot drive a physical iPhone (simulators only), and a phone with a work profile can stall its
driver install; an emulator avoids both.
