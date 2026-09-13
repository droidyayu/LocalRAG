#!/usr/bin/env python3
"""Build-time embedding sidecar for LocalRAG.

Loads EmbeddingGemma once and embeds every chunk handed to it, so the Gradle plugin pays the model
load cost once per build rather than once per document.

Protocol, shared with hash_embed.py: one JSON object per line on stdin with a "text" field, one
JSON array of floats per line on stdout, in the same order. Vectors are L2 normalized here as well
as in the plugin, so the runtime cosine stays a plain dot product either way.

Setup is documented in README.md. The model is gated, so a machine that has not accepted the
licence will fail on load rather than silently produce nothing.
"""

import argparse
import json
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", default="google/embeddinggemma-300m")
    parser.add_argument("--dimensions", type=int, default=256)
    parser.add_argument("--batch-size", type=int, default=32)
    args = parser.parse_args()

    # Imported lazily so --help works on a machine without the toolchain installed.
    from sentence_transformers import SentenceTransformer

    model = SentenceTransformer(args.model)

    texts = []
    for line in sys.stdin:
        line = line.strip()
        if line:
            texts.append(json.loads(line)["text"])

    if not texts:
        return 0

    vectors = model.encode(
        texts,
        batch_size=args.batch_size,
        normalize_embeddings=True,
        show_progress_bar=False,
    )

    for vector in vectors:
        values = [float(value) for value in vector[: args.dimensions]]
        if len(values) != args.dimensions:
            print(
                f"model returned {len(vector)} dimensions, cannot satisfy {args.dimensions}",
                file=sys.stderr,
            )
            return 1
        print(json.dumps(values), flush=True)

    return 0


if __name__ == "__main__":
    sys.exit(main())
