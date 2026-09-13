#!/usr/bin/env python3
"""Deterministic stand-in for the real embedding sidecar.

It produces stable pseudo-vectors from a hash of the text. They carry no semantic meaning, so they
are useless for retrieval quality, but they exercise the whole pipeline - the sidecar protocol, the
incremental embed step, vectors.bin, and the manifest parity block - without a 1.2 GB model or a
licence acceptance.

Use it to develop and test the plugin. Use embed.py for real vectors.

Protocol, shared with embed.py: one JSON object per line on stdin with a "text" field, one JSON
array of floats per line on stdout, in the same order.
"""

import argparse
import hashlib
import json
import math
import sys


def embed(text: str, dimensions: int) -> list[float]:
    values: list[float] = []
    counter = 0
    while len(values) < dimensions:
        digest = hashlib.sha256(f"{counter}:{text}".encode("utf-8")).digest()
        for index in range(0, len(digest), 2):
            if len(values) == dimensions:
                break
            pair = int.from_bytes(digest[index : index + 2], "big")
            values.append(pair / 32767.5 - 1.0)
        counter += 1

    magnitude = math.sqrt(sum(value * value for value in values))
    if magnitude == 0.0:
        return values
    return [value / magnitude for value in values]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dimensions", type=int, default=256)
    args = parser.parse_args()

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        request = json.loads(line)
        print(json.dumps(embed(request["text"], args.dimensions)), flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
