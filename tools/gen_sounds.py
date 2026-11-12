#!/usr/bin/env python3
"""Synthesises every Gorewidow sound from scratch (no samples) and writes OGG Vorbis + sounds.json.

All audio is generated procedurally from oscillators, noise and filters, so it is original and
free of third-party licensing. Requires numpy, scipy and ffmpeg (libvorbis).
Run: python3 tools/gen_sounds.py
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np
from scipy import signal

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/gorewidow/sounds")
SR = 44100
rng = np.random.default_rng(2024)


# ------------------------------------------------------------------ primitives
def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def noise(dur):
    return rng.standard_normal(int(SR * dur))


def env_adsr(n, a, d, s, r, sustain_level=0.6):
    a, d, r = int(a * SR), int(d * SR), int(r * SR)
    s_len = max(0, n - a - d - r)
    e = np.concatenate([
        np.linspace(0, 1, max(a, 1)),
        np.linspace(1, sustain_level, max(d, 1)),
        np.full(s_len, sustain_level),
        np.linspace(sustain_level, 0, max(r, 1)),
    ])
    return np.pad(e, (0, max(0, n - len(e))))[:n]


def exp_decay(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def bandpass(x, lo, hi, order=2):
    lo = max(20.0, lo)
    hi = min(SR / 2 - 100, hi)
    sos = signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def lowpass(x, f, order=2):
    sos = signal.butter(order, min(f, SR / 2 - 100), btype="low", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def highpass(x, f, order=2):
    sos = signal.butter(order, f, btype="high", fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def resonator(x, freq, q):
    b, a = signal.iirpeak(freq, q, fs=SR)
    return signal.lfilter(b, a, x)


def swept_bandpass(x, f_start, f_end, q=6.0, blocks=64):
    """Time-varying band-pass by processing short overlapping blocks."""
    n = len(x)
    out = np.zeros(n)
    win = max(256, n // blocks)
    hop = win // 2
    window = np.hanning(win)
    for i, start in enumerate(range(0, n, hop)):
        seg = x[start:start + win]
        if len(seg) < 8:
            break
        frac = start / max(1, n - 1)
        f = f_start * (f_end / f_start) ** frac
        y = resonator(np.pad(seg, (0, win - len(seg))), f, q)
        out[start:start + win] += (y * window)[:len(seg)]
    return out


def saw(phase):
    return 2.0 * (phase - np.floor(phase + 0.5))


def osc(freq_curve, kind="sine"):
    phase = np.cumsum(freq_curve) / SR
    if kind == "saw":
        return saw(phase)
    if kind == "square":
        return np.sign(np.sin(2 * np.pi * phase))
    return np.sin(2 * np.pi * phase)


def distort(x, drive):
    return np.tanh(x * drive) / np.tanh(drive)


def click_train(dur, rate_curve, click_len=0.004, jitter=0.25, decay=0.0012):
    n = int(SR * dur)
    out = np.zeros(n)
    t = 0.0
    while t < dur:
        rate = rate_curve(t / dur)
        i = int(t * SR)
        L = int(click_len * SR)
        if i + L < n:
            c = rng.standard_normal(L) * exp_decay(L, decay)
            out[i:i + L] += c * rng.uniform(0.5, 1.0)
        t += (1.0 / rate) * (1 + rng.uniform(-jitter, jitter))
    return out


def place(dst, src, at):
    i = int(at * SR)
    j = min(len(dst), i + len(src))
    if i < len(dst):
        dst[i:j] += src[:j - i]
    return dst


def finish(x, peak_db=-1.0, fade=0.006):
    x = x - np.mean(x)
    f = int(fade * SR)
    if len(x) > 2 * f:
        x[:f] *= np.linspace(0, 1, f)
        x[-f:] *= np.linspace(1, 0, f)
    m = np.max(np.abs(x)) + 1e-9
    return x / m * (10 ** (peak_db / 20))


# ------------------------------------------------------------------ sound designs
def chitter(seed_shift=0):
    dur = 1.1 + 0.4 * rng.random()
    burst_rate = lambda p: 35 + 25 * np.sin(p * np.pi * (2 + seed_shift))
    clicks = click_train(dur, burst_rate, click_len=0.005, decay=0.0009)
    body = resonator(clicks, 2600 + 400 * seed_shift, 6) + 0.7 * resonator(clicks, 4100, 8) + 0.4 * resonator(clicks, 1300, 4)
    gate = np.zeros(len(body))
    # 3-5 chitter phrases with gaps
    t = 0.0
    while t < dur:
        L = rng.uniform(0.12, 0.3)
        a, b = int(t * SR), int(min(dur, t + L) * SR)
        gate[a:b] = np.hanning(b - a) if b > a else 0
        t += L + rng.uniform(0.05, 0.18)
    breath = bandpass(noise(dur), 300, 1500) * 0.15 * env_adsr(len(gate), 0.2, 0.3, 0.5, 0.3, 0.5)
    return finish(body * gate + breath, -3)


def hurt():
    dur = 0.45
    t = t_axis(dur)
    f = 950 * np.exp(-t * 2.2) + 380
    tone = osc(f * (1 + 0.04 * np.sin(2 * np.pi * 38 * t)), "saw")
    tone = bandpass(tone, 600, 4200)
    hiss = swept_bandpass(noise(dur), 5000, 1800, q=3)
    x = (tone * 0.8 + hiss * 1.2) * env_adsr(len(t), 0.005, 0.08, 0.5, 0.25, 0.55)
    return finish(distort(x * 1.2, 1.6), -3)


def death():
    dur = 2.0
    t = t_axis(dur)
    f = 760 * np.exp(-t * 0.9) + 120
    voice = osc(f * (1 + 0.06 * np.sin(2 * np.pi * (14 - 6 * t) * t)), "saw") + 0.5 * osc(f * 1.51, "saw")
    voice = resonator(voice, 900, 3) + resonator(voice, 2200, 5) * 0.6
    voice *= env_adsr(len(t), 0.02, 0.4, 0.4, 1.2, 0.5)
    rattle = click_train(dur, lambda p: 40 - 30 * p, decay=0.002) * np.linspace(1, 0, len(t)) ** 2
    rattle = resonator(rattle, 1800, 4)
    crunch = lowpass(noise(0.35), 1800) * exp_decay(int(0.35 * SR), 0.07)
    x = voice * 0.7 + rattle * 0.5
    place(x, crunch * 0.8, 1.35)
    return finish(distort(x * 1.5, 1.8), -2)


def step(i):
    dur = 0.07
    n = int(dur * SR)
    tap = np.sin(2 * np.pi * (2200 + 450 * i) * t_axis(dur)) * exp_decay(n, 0.006)
    grit = highpass(noise(dur), 2500) * exp_decay(n, 0.004) * 0.6
    thud = np.sin(2 * np.pi * (160 + 30 * i) * t_axis(dur)) * exp_decay(n, 0.012) * 0.5
    return finish(tap + grit + thud, -4)


def bite(i):
    dur = 0.35
    n = int(dur * SR)
    snap = highpass(noise(0.02), 1500) * exp_decay(int(0.02 * SR), 0.003)
    crunch = bandpass(noise(dur), 500, 3500) * exp_decay(n, 0.05)
    crackle = click_train(dur, lambda p: 180 - 120 * p, click_len=0.002, decay=0.0006) * exp_decay(n, 0.08)
    wet = lowpass(noise(dur), 600) * exp_decay(n, 0.09) * 0.8
    x = crunch * 0.8 + crackle * 0.6 + wet
    place(x, snap * 2.5, 0.0)
    place(x, snap * (1.5 + 0.5 * i), 0.07 + 0.02 * i)
    return finish(x, -1.5)


def screech(i):
    dur = 1.05
    t = t_axis(dur)
    base = (520 + 60 * i) * (1 + 0.35 * np.sin(np.pi * np.clip(t / 0.25, 0, 1) / 2)) * (1 - 0.25 * np.clip((t - 0.6) / 0.45, 0, 1))
    vib = 1 + 0.03 * np.sin(2 * np.pi * 22 * t)
    voices = sum(osc(base * vib * d, "saw") for d in (1.0, 1.007, 0.993, 2.01))
    formant = resonator(voices, 1100, 4) + resonator(voices, 2700, 6) * 0.8 + resonator(voices, 4300, 8) * 0.4
    hiss = swept_bandpass(noise(dur), 3000, 6000, q=2) * 0.5
    x = (formant + hiss) * env_adsr(len(t), 0.03, 0.15, 0.8, 0.45, 0.8)
    return finish(distort(x * 0.9, 1.4), -4)


def drain(i):
    dur = 0.9
    n = int(dur * SR)
    t = t_axis(dur)
    slurp = lowpass(noise(dur), 900) * (0.6 + 0.4 * np.sin(2 * np.pi * (3.5 + i) * t) ** 2)
    slurp = resonator(slurp, 320 + 60 * i, 3) * 2 + slurp
    x = slurp * env_adsr(n, 0.05, 0.1, 0.8, 0.25, 0.8)
    for _ in range(9):
        L = rng.uniform(0.03, 0.06)
        bt = t_axis(L)
        f0 = rng.uniform(300, 700)
        pop = np.sin(2 * np.pi * np.cumsum(f0 * (1 + 3 * bt / L)) / SR) * np.hanning(len(bt))
        place(x, pop * rng.uniform(0.3, 0.7), rng.uniform(0.05, dur - 0.1))
    return finish(x, -2)


def purr():
    dur = 1.6
    t = t_axis(dur)
    rumble = lowpass(noise(dur), 220) * (0.5 + 0.5 * np.sin(2 * np.pi * 24 * t)) ** 2
    rumble = resonator(rumble, 110, 2) * 2 + rumble
    clicks = resonator(click_train(dur, lambda p: 12, decay=0.002), 1500, 5) * 0.15
    x = (rumble + clicks) * env_adsr(len(t), 0.2, 0.2, 0.9, 0.5, 0.9)
    return finish(x, -3)


def leap():
    dur = 0.45
    n = int(dur * SR)
    whoosh = swept_bandpass(noise(dur), 400, 2400, q=2) * np.hanning(n)
    hiss = bandpass(noise(dur), 2500, 7000) * exp_decay(n, 0.08) * 0.4
    return finish(whoosh + hiss, -2)


def land():
    dur = 0.6
    n = int(dur * SR)
    t = t_axis(dur)
    thump = np.sin(2 * np.pi * (90 * np.exp(-t * 6) + 45) * t) * exp_decay(n, 0.08)
    dirt = lowpass(noise(dur), 2000) * exp_decay(n, 0.05) * 0.5
    x = thump + dirt
    for k in range(6):
        place(x, step(k % 4) * 0.35, 0.05 + 0.05 * k + rng.uniform(0, 0.02))
    return finish(x, -1)


def web_shoot(i):
    dur = 0.28
    n = int(dur * SR)
    thwip = swept_bandpass(noise(dur), 6500 - 800 * i, 1300, q=5, blocks=40) * exp_decay(n, 0.07)
    chirp = osc(np.linspace(2600, 700, n)) * exp_decay(n, 0.04) * 0.4
    puff = lowpass(noise(dur), 900) * exp_decay(n, 0.02) * 0.6
    return finish(thwip * 1.4 + chirp + puff, -1.5)


def web_hit(i):
    dur = 0.32
    n = int(dur * SR)
    t = t_axis(dur)
    thud = np.sin(2 * np.pi * (140 + 30 * i) * t) * exp_decay(n, 0.03)
    sticky = click_train(dur, lambda p: 260 - 200 * p, click_len=0.0025, decay=0.0005) * exp_decay(n, 0.09)
    sticky = bandpass(sticky, 1200, 6000)
    splat = bandpass(noise(dur), 300, 2500) * exp_decay(n, 0.025)
    return finish(thud + sticky * 0.9 + splat * 0.7, -1.5)


def web_retract():
    dur = 0.42
    n = int(dur * SR)
    t = t_axis(dur)
    zip_ = swept_bandpass(noise(dur), 700, 5200, q=6, blocks=50)
    zip_ *= 0.55 + 0.45 * np.sign(np.sin(2 * np.pi * (60 + 140 * t / dur) * t))
    return finish(zip_ * np.hanning(n) ** 0.5, -2)


def web_strain(i):
    dur = 0.65
    t = t_axis(dur)
    f = (95 + 25 * i) * (1 + 0.15 * t / dur)
    creak = osc(f, "saw")
    creak = resonator(creak, 700 + 150 * i, 8) + resonator(creak, 1900, 10) * 0.5
    stick = click_train(dur, lambda p: 70 + 40 * np.sin(p * 9), click_len=0.003, decay=0.0007)
    stick = bandpass(stick, 1500, 5000)
    x = (creak * 0.7 + stick * 0.5) * env_adsr(len(t), 0.06, 0.1, 0.8, 0.2, 0.8)
    return finish(x, -3)


DESIGNS = {
    "ambient": [lambda: chitter(0), lambda: chitter(1), lambda: chitter(2)],
    "hurt": [hurt, hurt],
    "death": [death],
    "step": [lambda i=i: step(i) for i in range(4)],
    "bite": [lambda: bite(0), lambda: bite(1)],
    "screech": [lambda: screech(0), lambda: screech(1)],
    "drain": [lambda: drain(0), lambda: drain(1)],
    "purr": [purr],
    "leap": [leap],
    "land": [land],
    "web_shoot": [lambda: web_shoot(0), lambda: web_shoot(1)],
    "web_hit": [lambda: web_hit(0), lambda: web_hit(1)],
    "web_retract": [web_retract],
    "web_strain": [lambda: web_strain(0), lambda: web_strain(1)],
}


def write_ogg(samples, path):
    pcm = (np.clip(samples, -1, 1) * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "5", path], check=True)
    os.unlink(wav_path)


def main():
    os.makedirs(OUT, exist_ok=True)
    sounds_json = {}
    for name, makers in DESIGNS.items():
        files = []
        for idx, make in enumerate(makers):
            fname = f"{name}{idx + 1}"
            write_ogg(make(), os.path.join(OUT, fname + ".ogg"))
            files.append({"name": f"gorewidow:{fname}", "attenuation_distance": 24 if name in ("screech", "death") else 16})
        sounds_json[f"entity.gorewidow.{name}"] = {"subtitle": f"subtitles.gorewidow.{name}", "sounds": files}
    with open(os.path.join(os.path.dirname(OUT), "sounds.json"), "w") as f:
        json.dump(sounds_json, f, indent=2)
    print(f"{sum(len(v) for v in DESIGNS.values())} sounds written")


if __name__ == "__main__":
    main()
