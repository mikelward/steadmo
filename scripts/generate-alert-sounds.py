#!/usr/bin/env python3
"""Synthesizes Steadmo's bundled low and urgent-low alert tones into app/src/main/res/raw/."""

import math
import pathlib
import struct
import wave

SAMPLE_RATE = 22050
PEAK_AMPLITUDE = 0.85


def render_tone(
    notes: list[float],
    note_ms: int,
    gap_ms: int,
    loop_pause_ms: int,
    harmonics: list[float],
    decay: float,
) -> list[int]:
    note_sec = note_ms / 1000.0
    step_sec = (note_ms + gap_ms) / 1000.0
    attack_sec = 0.012
    release_sec = 0.025
    phrase_sec = len(notes) * step_sec - (gap_ms / 1000.0) + release_sec
    total_sec = phrase_sec + (loop_pause_ms / 1000.0)
    total_samples = int(round(total_sec * SAMPLE_RATE))
    buf = [0.0] * total_samples
    h_sum = sum(harmonics)
    end_level = max(0.08, 1.0 - decay + 0.15)

    for idx, freq in enumerate(notes):
        t0 = idx * step_sec
        t_end = t0 + note_sec
        start_i = int(round(t0 * SAMPLE_RATE))
        stop_i = min(total_samples, int(round((t_end + release_sec) * SAMPLE_RATE)))
        for i in range(start_i, stop_i):
            t = i / SAMPLE_RATE - t0
            if t < 0.0:
                continue
            if t < attack_sec:
                env = t / attack_sec
            elif t < note_sec - 0.02:
                frac = (t - attack_sec) / max(1e-6, (note_sec - 0.02) - attack_sec)
                env = end_level**frac
            elif t < note_sec + release_sec:
                frac = (t - (note_sec - 0.02)) / (0.02 + release_sec)
                env = end_level * (0.0001 / end_level) ** frac
            else:
                env = 0.0
            sample = 0.0
            for h_idx, amp in enumerate(harmonics):
                sample += (amp / h_sum) * math.sin(2.0 * math.pi * freq * (h_idx + 1) * t)
            buf[i] += sample * env

    peak = max((abs(x) for x in buf), default=1.0)
    scale = (PEAK_AMPLITUDE * 32767.0) / max(peak, 1e-6)
    return [max(-32767, min(32767, int(round(x * scale)))) for x in buf]


def write_wav(path: pathlib.Path, samples: list[int]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(SAMPLE_RATE)
        wf.writeframes(struct.pack(f"<{len(samples)}h", *samples))


def main() -> None:
    raw_dir = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/raw"

    # Low (< 3.9 mmol/L): three same-pitch warm C5 pulses, with trailing silence for looping.
    low = render_tone(
        notes=[523.25, 523.25, 523.25],
        note_ms=170,
        gap_ms=110,
        loop_pause_ms=900,
        harmonics=[1.0, 0.45, 0.18],
        decay=0.72,
    )
    write_wav(raw_dir / "alert_low.wav", low)

    # Urgent low (< 3.0 mmol/L or low heading there): three descending C-major notes (G5 -> E5 -> C5).
    urgent = render_tone(
        notes=[783.99, 659.25, 523.25],
        note_ms=175,
        gap_ms=85,
        loop_pause_ms=750,
        harmonics=[1.0, 0.45, 0.20],
        decay=0.72,
    )
    write_wav(raw_dir / "alert_urgent_low.wav", urgent)


if __name__ == "__main__":
    main()
