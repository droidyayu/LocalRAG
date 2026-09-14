"""LiteRT-LM generation backend for the agent PoC (needs tools/agent-poc/.venv).

Mirrors localrag-android Generator: one Engine per process, a fresh conversation
per generate() call (no turn leaks into the next), greedy sampling, closed
explicitly. Returns None on any failure so the loop falls back, like on device.
"""

import sys


class LiteRtGenerator:
    def __init__(self, model_path, system_instruction, max_output_tokens=256,
                 cache_dir=None, temperature=0.0):
        import os

        import litert_lm

        if cache_dir is not None:
            os.makedirs(cache_dir, exist_ok=True)
        litert_lm.set_min_log_severity(litert_lm.LogSeverity.ERROR)
        self._litert_lm = litert_lm
        self._system = system_instruction
        self._max_tokens = max_output_tokens
        self._sampler = litert_lm.SamplerConfig(
            top_k=1, top_p=1.0, temperature=temperature)
        self._engine = litert_lm.Engine(
            model_path,
            backend=litert_lm.Backend.CPU(),
            cache_dir=cache_dir,
        )
        self._shapes_logged = False

    def generate(self, prompt):
        litert_lm = self._litert_lm
        conversation = self._engine.create_conversation(
            system_message=self._system,
            sampler_config=self._sampler,
            max_output_tokens=self._max_tokens,
        )
        try:
            response = conversation.send_message(prompt)
            return self._text_of(response)
        except Exception as failure:  # noqa: BLE001 - parity with runCatching fallback
            print(f"  [generate failed: {failure}]", file=sys.stderr)
            return None
        finally:
            try:
                conversation.close()
            except Exception:  # noqa: BLE001 - close is best-effort
                pass

    def _text_of(self, response):
        if isinstance(response, str):
            return response
        if isinstance(response, dict):
            if not self._shapes_logged:
                print(f"  [response keys: {sorted(response.keys())}]",
                      file=sys.stderr)
                self._shapes_logged = True
            for key in ("content", "text", "response", "output"):
                value = response.get(key)
                if isinstance(value, str) and value:
                    return value
                if isinstance(value, list):
                    # [{"type": "text", "text": "..."}, ...]
                    texts = [
                        part.get("text", "")
                        for part in value
                        if isinstance(part, dict)
                    ]
                    joined = "".join(texts)
                    if joined:
                        return joined
                if isinstance(value, dict):
                    inner = value.get("content") or value.get("text")
                    if isinstance(inner, str) and inner:
                        return inner
            return None
        return str(response) if response is not None else None

    def close(self):
        try:
            self._engine.close()
        except Exception:  # noqa: BLE001 - best-effort
            pass
