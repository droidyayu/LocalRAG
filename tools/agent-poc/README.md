# Agent-loop PoC (stdlib only — `python3`, no venv, no installs)

Measures what can be measured without a device, and replays what the device did.

## Commands

```bash
python3 tools/agent-poc/agent_poc.py self-test
python3 tools/agent-poc/agent_poc.py sizes --question "how do I deposit funds"
python3 tools/agent-poc/agent_poc.py demo
python3 tools/agent-poc/agent_poc.py replay --transcript /tmp/turn.txt
```

- `sizes`: BM25 over the real `app/src/main/docs`, then the round-0 prompt size
  plus per-round projections. A/B context levers with `--top-k`,
  `--max-obs-chars N`, and `--prompt-file variant.txt`.
- `demo`: three scripted turns against the worked-example figures — verbatim
  (passes), rounded (rejected), preamble-before-forced-answer (unresolved).
- `replay`: paste real device output into the `Q:`/`M:`/`O <tool>:` format from
  the script docstring and get step-by-step verdicts plus prompt sizes.
- `self-test`: pins the port against known protocol/gate behavior.

## Fidelity notes (read before citing numbers)

- The system prompt is a **copy** of `AGENT_SYSTEM_PROMPT` — re-sync after
  prompt edits (the current copy includes the verbatim-figures rule).
- Tokenizer, protocol, and gate are line-by-line ports for experimentation;
  the JVM suites (`AgentProtocolTest`, `OutputGateTest`, `AgentRunnerTest`)
  remain the contract. Any behavioral divergence found here must be fixed
  there first, then re-ported.
- Tokens are chars/4 estimates, consistent across runs, not model truth.
- Paragraph chunks approximate the 53 bundle chunks; retrieval ranking is
  indicative, not the shipped BM25.
- Observation text uses the worked-example figures; per-tool renderers and
  trimming experiments go through `--max-obs-chars` and transcript files.
