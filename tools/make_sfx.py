"""Synthesizes SaanNaPunta's UI sound kit.

Physical-modelling style after earshot (github.com/Leonxlnx/earshot, MIT): a short raised-cosine contact
pulse excites damped modes (pops, knocks) or inharmonic bell partials (chimes). Parameters follow earshot's
published bank figures (ui_pop soft 550 Hz / 0.14 s, blip 1030 Hz, knock wood 400 Hz, mallet celesta G5,
C-pentatonic runs). Written from scratch; no earshot code is executed.

usage: python make_sfx.py OUT_DIR
"""
import os
import sys
import wave

import numpy as np

SR = 44100
rng = np.random.default_rng(7)


def t_of(seconds):
    return np.arange(int(SR * seconds)) / SR


def contact(seconds, bright=0.5):
    n = max(2, int(SR * seconds))
    pulse = 0.5 - 0.5 * np.cos(np.linspace(0, 2 * np.pi, n))
    return pulse + rng.standard_normal(n) * pulse * bright


def modal(freqs, decays, amps, seconds, glide=0.0):
    t = t_of(seconds)
    out = np.zeros_like(t)
    for f, d, a in zip(freqs, decays, amps):
        inst = f * (1 - glide * (1 - np.exp(-t / d)))
        ph = 2 * np.pi * np.cumsum(inst) / SR + rng.uniform(0, 2 * np.pi)
        out += a * np.exp(-t / d) * np.sin(ph)
    return out


def excite(body, exc):
    """convolve a resonator response with a contact pulse: a soft strike instead of a click-on"""
    y = np.convolve(exc, body)[: len(body)]
    return y


def bell(f0, seconds, decay=0.5, bright=0.35):
    ratios = [1.0, 2.76, 5.40, 8.93]  # free bar (glockenspiel / celesta) partials
    amps = [1.0, 0.45, 0.2, 0.08]
    t = t_of(seconds)
    y = np.zeros_like(t)
    for q, a in zip(ratios, amps):
        a = a * q ** (1.2 * (bright - 0.5))
        env = a * np.exp(-t / (decay * q ** -0.8))
        b = rng.uniform(0.5, 1.5)
        y += env * (np.sin(2 * np.pi * f0 * q * t + rng.uniform(0, 6.3)) +
                    0.7 * np.sin(2 * np.pi * (f0 * q + b) * t + rng.uniform(0, 6.3))) / 1.7
    k = int(SR * 0.0015)
    y[:k] *= np.linspace(0, 1, k)
    return y


def place(total, parts):
    """mix [(start_s, signal, gain)] into one buffer of `total` seconds"""
    out = np.zeros(int(SR * total))
    for start, sig, gain in parts:
        i = int(SR * start)
        n = min(len(sig), len(out) - i)
        out[i:i + n] += gain * sig[:n]
    return out


def finish(y, peak_db):
    y = y - np.mean(y)
    f = int(SR * 0.012)
    y[-f:] *= np.linspace(1, 0, f)
    y[0] = 0.0
    return y / (np.abs(y).max() + 1e-12) * 10 ** (peak_db / 20)


def pop(f, dur, decay, glide=0.0, onset=0.012, bright=0.15):
    body = modal([f, f * 1.51, f * 2.3], [decay, decay * 0.45, decay * 0.25], [1.0, 0.3, 0.1], dur, glide)
    return excite(body, contact(onset, bright))


def knock(f, dur):
    body = modal([f, f * 1.68, f * 2.9, f * 4.2], [0.03, 0.018, 0.01, 0.006], [1.0, 0.6, 0.35, 0.2], dur, 0.01)
    return excite(body, contact(0.0015, 0.8))


SOUNDS = {
    # light tap for buttons, chips and list rows (ui_pop soft)
    "tap": lambda: finish(pop(550, 0.12, 0.022, onset=0.010), -10),
    # tiny tick for selecting an option (ui_pop blip)
    "select": lambda: finish(pop(1030, 0.06, 0.010, onset=0.004, bright=0.25), -12),
    # switch on: two quick rising ticks; off: one lower tick
    "toggle_on": lambda: finish(place(0.12, [(0, pop(760, 0.06, 0.010, onset=0.003), 0.7),
                                               (0.035, pop(1020, 0.08, 0.012, onset=0.003), 1.0)]), -11),
    "toggle_off": lambda: finish(pop(620, 0.08, 0.012, onset=0.003, glide=0.03), -12),
    # next step: soft rising two-note pop (drip plink feel without a synth sweep)
    "step_next": lambda: finish(place(0.2, [(0, pop(660, 0.1, 0.02, onset=0.006), 0.8),
                                             (0.06, pop(880, 0.14, 0.026, onset=0.006), 1.0)]), -11),
    "step_back": lambda: finish(place(0.2, [(0, pop(880, 0.1, 0.02, onset=0.006), 0.8),
                                             (0.06, pop(660, 0.14, 0.026, onset=0.006), 1.0)]), -12),
    # saved / confirmed: one soft celesta note pair (G5 -> D6)
    "success": lambda: finish(place(0.6, [(0, bell(784, 0.5, decay=0.28), 0.8),
                                           (0.07, bell(1175, 0.5, decay=0.3), 1.0)]), -9),
    # setup complete: C-pentatonic run C5 E5 G5 C6, calm and short
    "complete": lambda: finish(place(1.0, [(0.00, bell(523, 0.8, decay=0.35), 0.75),
                                            (0.09, bell(659, 0.8, decay=0.35), 0.8),
                                            (0.18, bell(784, 0.8, decay=0.4), 0.9),
                                            (0.30, bell(1047, 0.7, decay=0.45), 1.0)]), -8),
    # can't do that / validation: double wood knock 55 ms apart (knock double)
    "error": lambda: finish(place(0.16, [(0, knock(400, 0.1), 1.0), (0.055, knock(380, 0.1), 0.8)]), -10),
    # removed: eased falling pop (ui_pop fall)
    "delete": lambda: finish(place(0.2, [(0, pop(890, 0.12, 0.02, glide=0.25, onset=0.006), 1.0),
                                          (0.07, pop(600, 0.12, 0.02, glide=0.2, onset=0.006), 0.5)]), -11),
    # permission granted: single bright tine (notify mid_tine)
    "granted": lambda: finish(bell(1046, 0.4, decay=0.22, bright=0.5), -11),
}


def write(path, y):
    pcm = (np.clip(y, -1, 1) * 32767).astype("<i2")
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())


if __name__ == "__main__":
    out = sys.argv[1]
    os.makedirs(out, exist_ok=True)
    for name, make in SOUNDS.items():
        y = make()
        write(os.path.join(out, f"sfx_{name}.wav"), y)
        print(f"sfx_{name}.wav {len(y) / SR:.2f}s")
