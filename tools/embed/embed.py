#!/usr/bin/env python3
"""Build-time embedding sidecar for LocalRAG, running on LiteRT-LM.

Deliberately the same runtime the Android app uses. litert-lm-api and litertlm-android ship the
same version and the same EmbeddingEngine surface, consume the same .litertlm model file, and
apply normalization and output truncation the same way. That is what keeps a vector built here
comparable to a query embedded on the phone; two different embedding stacks would agree on nothing
and fail silently rather than loudly.

Protocol, shared with hash_embed.py: one JSON object per line on stdin with a "text" field, one
JSON array of floats per line on stdout, in the same order. Any non-zero exit fails the build.
"""

import argparse
import json
import sys


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--model",
        required=True,
        help="path to the embedding .litertlm file, for example embeddinggemma-300m.litertlm",
    )
    parser.add_argument(
        "--dimensions",
        type=int,
        default=256,
        help="output size; EmbeddingGemma is Matryoshka so a prefix of the vector is valid",
    )
    parser.add_argument("--batch-size", type=int, default=32)
    parser.add_argument("--cache-dir", default=None, help="speeds up subsequent loads")
    args = parser.parse_args()

    # Imported lazily so --help works on a machine without the runtime installed.
    import litert_lm

    litert_lm.set_min_log_severity(litert_lm.LogSeverity.ERROR)

    texts = []
    for line in sys.stdin:
        line = line.strip()
        if line:
            texts.append(json.loads(line)["text"])

    if not texts:
        return 0

    options = litert_lm.EmbeddingOptions(
        # Normalizing here means the runtime cosine is a plain dot product.
        normalize=True,
        # EmbeddingGemma is Matryoshka trained, so truncating to a shorter prefix is a supported
        # trade rather than a lossy hack.
        output_size=args.dimensions,
    )

    with litert_lm.EmbeddingEngine(
        args.model,
        backend=litert_lm.Backend.CPU(),
        cache_dir=args.cache_dir,
    ) as engine:
        for start in range(0, len(texts), args.batch_size):
            batch = texts[start : start + args.batch_size]
            for response in engine.compute_embedding_batch(batch, options=options):
                vector = list(response.embedding)
                if len(vector) != args.dimensions:
                    print(
                        f"model returned {len(vector)} dimensions, expected {args.dimensions}",
                        file=sys.stderr,
                    )
                    return 1
                print(json.dumps(vector), flush=True)

    return 0


if __name__ == "__main__":
    sys.exit(main())
