#!/usr/bin/env python3
"""Agent-loop PoC: measure context, replay turns, A/B prompts — stdlib only.

What this is: a faithful *behavioral port* of the model-independent half of the
turn (AgentProtocol, OutputGate, Tokenizer, prompt assembly, transcript growth)
plus BM25 retrieval over the real docs corpus. The JVM unit tests remain the
contract; this exists to answer "how big is the context?" and "which reply
shapes survive the gate?" without a device.

What this is NOT: it cannot generate. The `generate` slot is filled by replay
files (paste real device output) or scripted candidates. Quality comparison of
prompt variants needs a model; everything here is sizes + gate verdicts.

Usage:
  python3 tools/agent-poc/agent_poc.py sizes --question "what is my portfolio worth"
  python3 tools/agent-poc/agent_poc.py demo
  python3 tools/agent-poc/agent_poc.py replay --transcript turns/worth.txt
  python3 tools/agent-poc/agent_poc.py self-test

Replay transcript format (device output pasted by hand):
  Q: and the profit
  H user: what is my portfolio worth
  H model: Your portfolio is worth $12,340.00.
  M:
  TOOL: get_portfolio_summary
  O get_portfolio_summary:
  total value: $12,340.00
  M:
  ANSWER: The profit is $1,240.00.
"""

import argparse
import math
import os
import re
import sys
import tempfile
import time
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DOCS = ROOT / "app/src/main/docs"

# ---------------------------------------------------------------- tokenizer
# Port of localrag-core Tokenizer: lowercase, keep & and %, drop stopwords.

STOPWORDS = {
    "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if",
    "in", "into", "is", "it", "of", "on", "or", "such", "that", "the",
    "their", "then", "there", "these", "they", "this", "to", "was",
    "will", "with",
}


def tokenize(text):
    out, cur = [], []
    for ch in text:
        if ch.isalnum() or ch in ("&", "%"):
            cur.append(ch.lower())
        elif cur:
            word = "".join(cur)
            cur = []
            if word not in STOPWORDS:
                out.append(word)
    if cur:
        word = "".join(cur)
        if word not in STOPWORDS:
            out.append(word)
    return out


# ---------------------------------------------------------------- protocol
# Port of AgentProtocol + the conversational-shape rule in AgentRunner.

def parse_tool_call(body, allowed):
    name = body.split("|", 1)[0].strip().lower()
    if not name or name not in allowed:
        return None
    args = {}
    rest = body.split("|", 1)[1] if "|" in body else ""
    if rest.strip():
        for part in rest.split(";"):
            if not part.strip():
                continue
            key = part.split("=", 1)[0].strip().lower()
            value = part.split("=", 1)[1].strip() if "=" in part else ""
            if not key or not value:
                return None
            args[key] = value
    return ("tool", name, args)


def parse(raw, allowed):
    """Returns ("tool", name, args) / ("answer", text) / None."""
    if raw is None:
        return None
    lines = [ln for ln in raw.splitlines() if ln.strip()]
    if not lines:
        return None
    first = lines[0].strip()
    low = first.lower()
    if low.startswith("tool:"):
        return parse_tool_call(first.split(":", 1)[1], allowed)
    if low.startswith("answer:"):
        text = raw.split(":", 1)[1].strip()
        return ("answer", text) if text else None
    return None


def contains_directive(text):
    return any(
        ln.strip().lower().startswith(("tool:", "answer:"))
        for ln in text.splitlines()
    )


def conversational_shape(text, limit=200):
    text = text.strip()
    return (
        bool(text)
        and len(text) <= limit
        and not any(ch.isdigit() for ch in text)
        and text[-1] in ".!?"
    )


# ---------------------------------------------------------------- gate
# Port of OutputGate.check with default Config (overlap 0.5, max 1200 chars).

# Tokens must end in a digit: separators live inside figures, never at the edge.
NUMBER = re.compile(r"\d(?:[\d,.]*\d)?")
ADVISORY = [
    "should you", "you should", "we recommend", "i recommend", "our advice",
    "will rise", "will fall", "will go up", "will go down", "good time to",
    "best time to", "worth buying", "worth selling", "i suggest", "we suggest",
    "you ought to", "is likely to increase", "is likely to decrease",
    "guaranteed",
]


def gate_check(answer, passages, minimum_overlap=0.5, max_chars=1200):
    text = answer.strip()
    if not text:
        return ("REJECTED", "EMPTY", "no text")
    if len(text) > max_chars:
        return ("REJECTED", "TOO_LONG", f"{len(text)} characters")
    source_numbers = set()
    for passage in passages:
        source_numbers.update(m.group(0) for m in NUMBER.finditer(passage))
    invented = sorted({m.group(0) for m in NUMBER.finditer(text)} - source_numbers)
    if invented:
        return ("REJECTED", "UNGROUNDED_DIGIT", "not present: " + ", ".join(invented))
    for phrase in ADVISORY:
        if phrase in text.lower():
            return ("REJECTED", "ADVISORY_LANGUAGE", phrase)
    if text[-1] not in ".!?":
        return ("REJECTED", "TRUNCATED", "does not end a sentence")
    answer_terms = tokenize(text)
    if not answer_terms:
        return ("REJECTED", "EMPTY", "no content words")
    source_terms = set()
    for passage in passages:
        source_terms.update(tokenize(passage))
    overlap = sum(1 for t in answer_terms if t in source_terms) / len(answer_terms)
    if overlap < minimum_overlap:
        return ("REJECTED", "LOW_OVERLAP", f"{overlap:.0%} of terms in passages")
    return ("ALLOWED", "", f"{overlap:.0%} overlap")


# ---------------------------------------------------------------- prompt
# Copy of AGENT_SYSTEM_PROMPT (keep in sync with AgentPrompts.kt). Override
# with --prompt-file to A/B variants and watch the size line move.

SYSTEM_PROMPT = """You are the in-app help assistant. Answer using only tool observations. Reply with exactly one line per turn: a TOOL call or an ANSWER.

TOOL lines look like: TOOL: tool_name | key=value; key=value
ANSWER lines look like: ANSWER: <one or two sentences>

Tools:
- get_portfolio_summary | (no arguments) : balances, total value, profit and loss
- get_category_summary | category=METALS, STOCKS, WEALTH or LEVERAGED
- get_margin_status | (no arguments) : margin used, free margin, margin level
- find_holding | query=<name or symbol> : one holding's figures
- search_documentation | query=<question> : help passages; cite nothing beyond them

Rules: never write a number that is not in the observations. Copy every figure character-for-character exactly as shown, cents and commas included ($12,340.00, never $12,340) — a rounded figure is a different figure. For how, what, or why questions about the app, call search_documentation first and answer only from its passages; never answer such questions from general knowledge. Never write OBSERVATION lines yourself; observations only arrive from tool results. Use only words from the observations; do not explain or add background. The ANSWER: line is the entire reply, with nothing written before it. If asked whether to buy, sell, or hold something, reply with ANSWER: I can't advise on buying or selling. Questions about fees, charges, costs, or prices are documentation questions, not advice: search first, never refuse them. Never advise buying, selling or holding. For greetings or questions with no relevant tool result, still answer with ANSWER: saying briefly what you can help with. For every new question, call the tool again for fresh figures even if earlier turns already show them; earlier turns are context, never a source of figures.

Example turn:
Question: what is my portfolio worth
TOOL: get_portfolio_summary
OBSERVATION [get_portfolio_summary]:
total value: $12,340.00
ANSWER: Your portfolio is worth $12,340.00.

Example turn:
Question: how do I deposit funds
TOOL: search_documentation | query=how do I deposit funds
OBSERVATION [search_documentation]:
How do I add funds? — overview
Transfer from a bank account held in your own name. Funds usually arrive within one working day.
ANSWER: Transfer from a bank account held in your own name. Funds usually arrive within one working day."""

# Demo observation: the worked example's figures.
DEMO_OBSERVATION = """total value: $12,340.00
total profit and loss: +$1,240.00
total profit and loss percent: +5.41%
available balance: $12,480.35
currency: USD"""

TOOL_NAMES = {
    "get_portfolio_summary", "get_category_summary", "get_margin_status",
    "find_holding", "search_documentation",
}


# ---------------------------------------------------------------- corpus
# Paragraph chunks over app/src/main/docs (approximation of bundle chunks).

def load_corpus():
    chunks = []
    for path in sorted(DOCS.glob("*.md")):
        title = path.stem.replace("-", " ")
        first_h1 = None
        paras = []
        for para in path.read_text().split("\n\n"):
            para = para.strip()
            if not para:
                continue
            if para.startswith("# ") and first_h1 is None:
                first_h1 = para[2:].strip()
                continue
            paras.append(para)
        for i, para in enumerate(paras):
            chunks.append({
                "id": f"{path.stem}#{i}",
                "title": first_h1 or title,
                "text": para,
            })
    return chunks


def bm25_search(query, chunks, top_k=4, k1=1.2, b=0.75):
    """Compact BM25 over chunk text. Ranking approximation only."""
    docs = [tokenize(c["title"] + " " + c["text"]) for c in chunks]
    lens = [len(d) for d in docs]
    avg = sum(lens) / max(1, len(lens))
    df = Counter()
    for d in docs:
        df.update(set(d))
    n = len(docs)
    scored = []
    for chunk, d in zip(chunks, docs):
        tf = Counter(d)
        score = 0.0
        for term in set(tokenize(query)):
            if term not in tf:
                continue
            idf = math.log(1 + (n - df[term] + 0.5) / (df[term] + 0.5))
            f = tf[term]
            score += idf * f * (k1 + 1) / (f + k1 * (1 - b + b * len(d) / avg))
        scored.append((score, chunk))
    scored.sort(key=lambda s: -s[0])
    return [c for s, c in scored[:top_k] if s > 0]


# ---------------------------------------------------------------- sizing
# Gemma-scale estimate: ~4 chars per token. Rough, but consistent across runs,
# which is all an A/B comparison needs.

def tokens(chars):
    return chars // 4


def size_line(label, text):
    print(f"  {label:<28} {len(text):>6} chars  ~{tokens(len(text)):>5} tok")


def transcript_prompt(system, transcript, question):
    return (transcript + "\n" if transcript else system) + f"\nQuestion: {question}\n"


# ---------------------------------------------------------------- turn runner
# Mirrors AgentRunner.answer control flow (3 rounds + forced answer).

FORCE_SUFFIX = (
    "No more tool calls. Reply now with one ANSWER: line "
    "using only the observations above.\n"
)


def history_block(history, max_chars=1500):
    """Mirror of AgentRunner.historyBlock: newest dropped first past the budget."""
    kept, chars = [], 0
    for role, text in reversed(history):
        if not text.strip():
            continue
        if kept and chars + len(text) > max_chars:
            break
        kept.append((role, text))
        chars += len(text)
    if not kept:
        return ""
    lines = ["Earlier in this conversation:"]
    for role, text in reversed(kept):
        lines.append(("User: " if role == "user" else "Assistant: ") + text.strip())
    return "\n".join(lines) + "\n"


def run_turn(question, replies, observations, system, history=(), max_rounds=3,
             max_obs_chars=None, generate=None, execute=None, echo=False):
    """replies: list of raw model outputs consumed in order (ignored when
    generate is set). observations: dict tool name -> text (missing = failed),
    unless execute(name, args) is given. history: list of (role, text).
    Returns the outcome string; prints sizes + verdicts along the way."""
    # Mirrors the runner: system seeded once, so every round re-reads it.
    transcript = system + history_block(history)
    seen_obs = []
    it = iter(replies or [])

    def gen(prompt_text, label):
        size_line(f"prompt to model ({label})", prompt_text)
        if generate is not None:
            start = time.time()
            out = generate(prompt_text)
            print(f"  [model took {time.time() - start:.0f}s]")
            if echo and out is not None:
                print(f"  model said: {out.strip()[:300]!r}")
            return out
        try:
            return next(it)
        except StopIteration:
            return None

    def judge(text):
        # Mirrors AgentRunner.judge exactly: Allowed -> FINAL, advisory ->
        # REFUSED, rejected-but-observation-free small talk -> FINAL, else
        # UNRESOLVED.
        answer = text.strip()
        if not answer:
            print("  judge -> UNRESOLVED (empty)")
            return "UNRESOLVED"
        evidence = seen_obs + ["".join(ch for ch in question if not ch.isdigit())]
        verdict = gate_check(answer, evidence)
        print(f"  judge -> {verdict[0]} {verdict[1]} {verdict[2]}")
        if verdict[0] == "ALLOWED":
            return "FINAL"
        if verdict[1] == "ADVISORY_LANGUAGE":
            return "REFUSED"
        if not seen_obs and conversational_shape(answer):
            return "FINAL"
        return "UNRESOLVED"

    for rnd in range(max_rounds):
        raw = gen(transcript + f"\nQuestion: {question}\n", f"round {rnd}")
        parsed = parse(raw, TOOL_NAMES)
        if parsed is None:
            prose = (raw or "").strip()
            if (not prose or seen_obs or contains_directive(prose)
                    or not conversational_shape(prose)):
                print("  round %d: off-grammar -> UNRESOLVED" % rnd)
                return "UNRESOLVED"
            print("  round %d: conversational" % rnd)
            return judge(prose)
        kind = parsed[0]
        if kind == "tool":
            _, name, args = parsed
            obs = execute(name, args) if execute is not None else observations.get(name)
            if obs is None:
                print(f"  round {rnd}: tool {name} failed -> UNRESOLVED")
                return "UNRESOLVED"
            if max_obs_chars is not None and len(obs) > max_obs_chars:
                obs = obs[:max_obs_chars] + "…[truncated]"
            print(f"  round {rnd}: TOOL {name} {args} ({len(obs)} obs chars)")
            seen_obs.append(obs)
            transcript += f"TOOL: {name}\nOBSERVATION [{name}]:\n{obs}\n"
        else:
            _, text = parsed
            if contains_directive(text):
                print(f"  round {rnd}: answer smuggles directive -> UNRESOLVED")
                return "UNRESOLVED"
            print(f"  round {rnd}: ANSWER ({len(text)} chars)")
            return judge(text)

    raw = gen(transcript + f"\nQuestion: {question}\n" + FORCE_SUFFIX, "forced")
    parsed = parse(raw, TOOL_NAMES)
    if (not isinstance(parsed, tuple) or parsed[0] != "answer"
            or contains_directive(parsed[1])):
        print("  forced answer off-grammar -> UNRESOLVED")
        return "UNRESOLVED"
    print("  forced ANSWER (%d chars)" % len(parsed[1]))
    return judge(parsed[1])


def read_transcript(path):
    """Q: / H user: / H model: / M: / O <tool>: blocks (see module docstring)."""
    question, replies, observations, history = "", [], {}, []
    mode, buf, tool = None, [], None
    def flush():
        if mode == "M":
            replies.append("\n".join(buf).strip())
        elif mode == "O":
            observations[tool] = "\n".join(buf).strip()
    for line in Path(path).read_text().splitlines():
        if line.startswith("Q:"):
            flush()
            mode = None
            question = line[2:].strip()
        elif line.startswith("H user:"):
            history.append(("user", line[len("H user:"):].strip()))
        elif line.startswith("H model:"):
            history.append(("model", line[len("H model:"):].strip()))
        elif line.startswith("M:"):
            flush()
            mode, buf = "M", []
        elif line.startswith("O "):
            flush()
            mode, buf = "O", []
            tool = line[2:].rstrip(":").strip()
        else:
            buf.append(line)
    flush()
    return question, replies, observations, history


# ---------------------------------------------------------------- commands

def cmd_sizes(args):
    system = Path(args.prompt_file).read_text() if args.prompt_file else SYSTEM_PROMPT
    chunks = load_corpus()
    print(f"corpus: {len(chunks)} paragraph chunks from {len(list(DOCS.glob('*.md')))} docs")
    print("context sizes (chars/4 ~= tokens):")
    size_line("system prompt", system)
    hits = bm25_search(args.question, chunks, top_k=args.top_k)
    obs_total = 0
    for chunk in hits:
        block = f"{chunk['title']}\n{chunk['text']}"
        obs_total += len(block)
        print(f"  hit {chunk['id']:<28} {len(block):>6} chars  {chunk['title'][:60]}")
    round0 = transcript_prompt(system, "", args.question)
    size_line("round-0 prompt (system+question)", round0)
    # Projection: each round appends one observation-sized block; system sent once.
    per_round_obs = obs_total // max(1, len(hits)) if hits else 200
    for rnd in range(1, 4):
        proj = len(round0) + rnd * (per_round_obs + 40)
        print(f"  projected round-{rnd} prompt{' (forced)' if rnd == 3 else '':<9} "
              f"{proj:>6} chars  ~{tokens(proj):>5} tok")
    if args.max_obs_chars:
        print(f"\nwith --max-obs-chars={args.max_obs_chars}: "
              f"each observation capped, projected round-3 "
              f"~{tokens(len(round0) + 3 * (min(per_round_obs, args.max_obs_chars) + 40))} tok")


DEMO_TURNS = [
    ("verbatim answer (should pass)",
     "what is my portfolio worth",
     ["TOOL: get_portfolio_summary",
      "ANSWER: Your portfolio is worth $12,340.00."],
     {"get_portfolio_summary": DEMO_OBSERVATION}, []),
    ("rounded figure (suspected device failure)",
     "what is my portfolio worth",
     ["TOOL: get_portfolio_summary",
      "ANSWER: Your portfolio is worth about $12,340."],
     {"get_portfolio_summary": DEMO_OBSERVATION}, []),
    ("preamble before forced answer (3 wasted rounds)",
     "what is my portfolio worth",
     ["TOOL: get_portfolio_summary",
      "TOOL: get_portfolio_summary",
      "TOOL: get_portfolio_summary",
      "Here is what I found. ANSWER: Your portfolio is worth $12,340.00."],
     {"get_portfolio_summary": DEMO_OBSERVATION}, []),
    ("follow-up with history (needs fresh tool data)",
     "and the profit",
     ["TOOL: get_portfolio_summary",
      "ANSWER: The profit is $1,240.00."],
     {"get_portfolio_summary": DEMO_OBSERVATION},
     [("user", "what is my portfolio worth"),
      ("model", "Your portfolio is worth $12,340.00.")]),
]


def cmd_demo(args):
    system = Path(args.prompt_file).read_text() if args.prompt_file else SYSTEM_PROMPT
    print("context sizes (chars/4 ~= tokens):")
    size_line("system prompt", system)
    size_line("demo observation", DEMO_OBSERVATION)
    for title, question, replies, observations, history in DEMO_TURNS:
        print(f"\n--- {title} ---")
        outcome = run_turn(question, list(replies), dict(observations), system,
                           history,
                           max_obs_chars=args.max_obs_chars)
        print(f"  outcome: {outcome}")


def cmd_replay(args):
    system = Path(args.prompt_file).read_text() if args.prompt_file else SYSTEM_PROMPT
    question, replies, observations, history = read_transcript(args.transcript)
    print(f"replaying {len(replies)} model replies "
          f"with {len(history)} history turns for: {question!r}")
    outcome = run_turn(question, replies, observations, system, history,
                       max_obs_chars=args.max_obs_chars)
    print(f"outcome: {outcome}")


def cmd_live(args):
    """Full turn against the real model: portfolio observations from --obs /
    --demo-obs, documentation from BM25 over the real corpus, everything else
    fails like a null tool result on device."""
    from litert_generate import LiteRtGenerator

    system = Path(args.prompt_file).read_text() if args.prompt_file else SYSTEM_PROMPT
    chunks = load_corpus()
    static_obs = {}
    if args.demo_obs:
        static_obs["get_portfolio_summary"] = DEMO_OBSERVATION
    for spec in args.obs or []:
        name, _, text = spec.partition("=")
        static_obs[name.strip()] = text
    history = []
    for item in args.hist or []:
        role, _, text = item.partition(":")
        history.append((role.strip(), text.strip()))

    def execute(name, tool_args):
        if name == "search_documentation":
            query = tool_args.get("query", "").strip()
            if not query:
                return None
            hits = bm25_search(query, chunks, top_k=args.top_k)
            if not hits:
                return "no passages found"
            return "\n---\n".join(
                f"{c['title']} — overview\n{c['text']}" for c in hits)
        return static_obs.get(name)

    cache_dir = args.cache_dir or os.path.join(tempfile.gettempdir(), "litert-cache")
    generator = LiteRtGenerator(args.model, system, max_output_tokens=256,
                                cache_dir=cache_dir)
    try:
        outcome = run_turn(args.question, [], {}, system, history,
                           max_obs_chars=args.max_obs_chars,
                           generate=generator.generate, execute=execute,
                           echo=True)
    finally:
        generator.close()
    print(f"outcome: {outcome}")


def cmd_self_test(_args):
    cases = [
        (parse("TOOL: get_portfolio_summary", TOOL_NAMES),
         ("tool", "get_portfolio_summary", {})),
        (parse("ANSWER: Hi there.", TOOL_NAMES), ("answer", "Hi there.")),
        (parse("small talk", TOOL_NAMES), None),
        (contains_directive("Hello!\nTOOL: x"), True),
        (conversational_shape("Hi there! Ask me anything."), True),
        (conversational_shape("Balance is 50000 rupees."), False),
        (gate_check("Your portfolio is worth $12,340.00.",
                    [DEMO_OBSERVATION, "what is my portfolio worth"])[0],
         "ALLOWED"),
        (gate_check("Your portfolio is worth about $12,340.",
                    [DEMO_OBSERVATION, "what is my portfolio worth"])[:2],
         ("REJECTED", "UNGROUNDED_DIGIT")),
        (gate_check("You should buy more stocks.",
                    [DEMO_OBSERVATION, "should i buy stocks"])[:2],
         ("REJECTED", "ADVISORY_LANGUAGE")),
    ]
    failed = 0
    for i, (got, want) in enumerate(cases):
        if got != want:
            failed += 1
            print(f"  case {i} FAILED: got {got!r}, want {want!r}")
    print(f"{len(cases) - failed}/{len(cases)} port checks passed")
    return 1 if failed else 0


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--prompt-file",
                    help="A/B a prompt variant without touching the app")
    ap.add_argument("--max-obs-chars", type=int, default=None,
                    help="cap observation text per tool (experiments with trimming)")
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("sizes", help="context sizes for a question over real docs")
    s.add_argument("--question", required=True)
    s.add_argument("--top-k", type=int, default=4)
    s.set_defaults(fn=cmd_sizes)
    d = sub.add_parser("demo", help="scripted turns showing gate verdicts")
    d.set_defaults(fn=cmd_demo)
    r = sub.add_parser("replay", help="replay a pasted device transcript")
    r.add_argument("--transcript", required=True)
    r.set_defaults(fn=cmd_replay)
    lv = sub.add_parser("live", help="full turn against a real .litertlm model")
    lv.add_argument("--question", required=True)
    lv.add_argument("--model", required=True, help="path to .litertlm generation model")
    lv.add_argument("--obs", action="append", metavar="NAME=TEXT",
                    help="canned observation for a portfolio tool (repeatable)")
    lv.add_argument("--demo-obs", action="store_true",
                    help="use the worked-example figures for get_portfolio_summary")
    lv.add_argument("--hist", action="append", metavar="ROLE:text",
                    help="history turn, ROLE is user or model (repeatable)")
    lv.add_argument("--top-k", type=int, default=4)
    lv.add_argument("--cache-dir", default=None,
                    help="engine cache dir (defaults to system tmp, never the repo)")
    lv.set_defaults(fn=cmd_live)
    t = sub.add_parser("self-test", help="pin the port behavior")
    t.set_defaults(fn=cmd_self_test)
    args = ap.parse_args(argv)
    result = args.fn(args)
    return result if isinstance(result, int) else 0


if __name__ == "__main__":
    sys.exit(main())

