#!/usr/bin/env python3
"""
Makes the PS5 style theme's menu sounds (src/main/resources/assets/v1zir/sounds/ui/*.ogg) from nothing: every sound
is synthesized here (sine tones, filtered noise and a made-up room), nothing is recorded or sampled.

One family of sounds in D major pentatonic: soft glassy tones whose pitch settles a little as they start (like a drop of
water), a warm tone under them, a breath of air, all in a small airy room.

    move         a short soft tick as the selection steps along
    tab          a breath of air sliding from left to right, a light sparkle where it lands
    confirm      two tones blooming upwards (a fifth)
    back         two tones settling downwards
    popup        a bubble rising as a small menu opens, a light ping where it stops
    popup_back   the bubble sinking as it closes
    popup_enter  a quick bright chime as one of its choices is taken
    notify       a soft pop and two chimes for a message
    boot         a slow swell of an open chord with sparkles climbing over it, when the game has loaded

Needs numpy and ffmpeg (with libvorbis). Run: python3 tools/ui_sounds.py [--wav DIR] (DIR keeps the WAVs too).
"""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np

SR = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'v1zir', 'sounds', 'ui')

# How loud each file is: its loudest 50 ms, K-weighted, in dB of full scale (as if mono). The game plays them at the
# volumes UiSounds gives, so with those the menus keep the loudness they had.
LOUDNESS = {
    'move': -15.0,
    'tab': -14.0,
    'confirm': -11.0,
    'back': -13.5,
    'popup': -13.5,
    'popup_back': -14.0,
    'popup_enter': -12.0,
    'notify': -11.0,
    'boot': -12.0,
}
PEAK_LIMIT_DB = -1.0


# ---------------------------------------------------------------- building blocks

def samples(seconds):
    return max(1, int(round(seconds * SR)))


def times(seconds):
    return np.arange(samples(seconds)) / SR


def hz(note):
    """A note's frequency, equal temperament, A4 = 440 Hz ('F#6' -> 1479.98)."""
    steps = {'C': -9, 'C#': -8, 'D': -7, 'D#': -6, 'E': -5, 'F': -4, 'F#': -3, 'G': -2, 'G#': -1, 'A': 0, 'A#': 1, 'B': 2}
    return 440.0 * 2.0 ** ((steps[note[:-1]] + 12 * (int(note[-1]) - 4)) / 12.0)


def smooth(x):
    """0 to 1 along a raised cosine (no corners, so no clicks)."""
    x = np.clip(x, 0.0, 1.0)
    return 0.5 - 0.5 * np.cos(np.pi * x)


def envelope(t, attack, decay, hold=0.0):
    """A smooth rise over `attack` seconds, `hold`, then an exponential fall with the time constant `decay`."""
    rise = smooth(t / attack) if attack > 0 else np.ones_like(t)
    return rise * np.exp(-np.clip(t - attack - hold, 0.0, None) / decay)


def sine(freq):
    """A sine following a frequency (Hz, a number or one per sample)."""
    return np.sin(2.0 * np.pi * np.cumsum(freq) / SR)


SOFT = ((2.0, 0.10, 0.35), (2.76, 0.05, 0.2))
CLEAR = ((2.0, 0.12, 0.4), (2.76, 0.07, 0.22), (5.4, 0.015, 0.08))
BELL = ((2.0, 0.13, 0.45), (3.0, 0.045, 0.3), (4.16, 0.035, 0.12))


def glass(freq, decay, seconds=None, attack=0.0012, drop=0.03, drop_time=0.01, partials=CLEAR):
    """
    A glassy tone: a sine whose pitch starts `drop` higher and settles, with a few partials (ratio, level, decay as a
    part of the tone's) that fade sooner, a bar's or a bell's.
    """
    t = times(seconds if seconds is not None else decay * 6.0)
    f = freq * (1.0 + drop * np.exp(-t / drop_time))
    out = sine(f) * envelope(t, attack, decay)
    for ratio, level, part in partials:
        if freq * ratio * (1.0 + drop) < 0.45 * SR:
            out += level * sine(f * ratio) * envelope(t, attack * 0.7, decay * part)
    return out


def warm(freq, decay, attack=0.004, seconds=None):
    """A plain soft sine under the glassy tones."""
    t = times(seconds if seconds is not None else decay * 6.0)
    return sine(np.full(len(t), freq)) * envelope(t, attack, decay)


def glide(f_from, f_to, length, decay, attack=0.004, hold=0.0, second=0.12, seconds=None):
    """A bubble: a tone gliding from one pitch to another (easing out) over `length` seconds as it fades."""
    t = times(seconds if seconds is not None else length + decay * 6.0)
    s = 1.0 - (1.0 - np.clip(t / length, 0.0, 1.0)) ** 2
    f = f_from * (f_to / f_from) ** s
    env = envelope(t, attack, decay, hold)
    return (sine(f) + second * sine(2.0 * f)) * env


def band(freqs, low, high, soft=0.2):
    """A smooth band (0 to 1) between two frequencies, its edges `soft` octaves wide."""
    lf = np.log2(np.maximum(freqs, 1.0))
    rise = 0.5 + 0.5 * np.tanh((lf - np.log2(low)) / soft)
    fall = 0.5 - 0.5 * np.tanh((lf - np.log2(high)) / soft)
    return rise * fall


def noise(rng, seconds, low, high):
    """Noise between two frequencies, its RMS 1."""
    n = samples(seconds)
    spectrum = np.fft.rfft(rng.standard_normal(n)) * band(np.fft.rfftfreq(n, 1.0 / SR), low, high)
    out = np.fft.irfft(spectrum, n)
    return out / (np.std(out) + 1e-12)


def swept_noise(rng, seconds, centre, octaves=1.0):
    """Noise through a band `octaves` wide whose middle follows centre(t) in Hz (short overlapping windows), RMS 1."""
    n = samples(seconds)
    frame, hop = 1024, 128
    source = rng.standard_normal(n + frame)
    window = np.hanning(frame)
    freqs = np.log2(np.maximum(np.fft.rfftfreq(frame, 1.0 / SR), 1.0))
    sigma = octaves / 2.355
    out = np.zeros(n + frame)
    weight = np.zeros(n + frame)
    for start in range(0, n, hop):
        middle = np.log2(centre((start + frame / 2) / SR))
        mask = np.exp(-0.5 * ((freqs - middle) / sigma) ** 2)
        out[start:start + frame] += np.fft.irfft(np.fft.rfft(source[start:start + frame] * window) * mask, frame) * window
        weight[start:start + frame] += window ** 2
    out = out[:n] / np.maximum(weight[:n], 1e-3)
    return out / (np.std(out) + 1e-12)


def room(rng, decay, predelay=0.012, dark=1.0):
    """
    A stereo room's answer to a click (two channels, each different, so it is wide): noise falling away by 60 dB in
    `decay` seconds, the highs sooner (`dark` makes them sooner still), after `predelay`.
    """
    n = samples(predelay + decay * 1.1)
    t = np.arange(n) / SR
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    bands = ((20.0, 350.0, 1.15), (350.0, 1800.0, 1.0), (1800.0, 5500.0, 0.75 / dark), (5500.0, 24000.0, 0.45 / dark))
    channels = []
    for _ in range(2):
        spectrum = np.fft.rfft(rng.standard_normal(n))
        answer = np.zeros(n)
        for low, high, part in bands:
            answer += np.fft.irfft(spectrum * band(freqs, low, high, 0.15), n) * np.exp(-6.91 * t / (decay * part))
        answer *= 1.0 - np.exp(-t / 0.01)
        shift = samples(predelay)
        channels.append(np.concatenate([np.zeros(shift), answer])[:n])
    out = np.array(channels)
    return out / np.sqrt(np.mean(np.sum(out ** 2, axis=1)))


def convolve(x, h):
    n = len(x) + len(h) - 1
    size = 1 << (n - 1).bit_length()
    return np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(h, size), size)[:n]


def one_pole(x, cutoff, high=False):
    """A gentle 6 dB per octave filter (low-pass, or high-pass)."""
    a = np.exp(-2.0 * np.pi * cutoff / SR)
    out = np.empty_like(x)
    state = 0.0
    for i, v in enumerate(x):
        state = (1.0 - a) * v + a * state
        out[i] = state
    return x - out if high else out


class Mix:
    """Sounds placed in time and across the stereo field, and how much of each goes into the room. It grows to fit."""

    def __init__(self):
        self.dry = np.zeros((2, 0))
        self.send = np.zeros(0)

    def add(self, x, at=0.0, pan=0.0, gain=1.0, send=1.0):
        x = np.asarray(x, dtype=np.float64) * gain
        fade = min(len(x) // 4, samples(0.02))
        if fade > 1:
            x[-fade:] *= 0.5 + 0.5 * np.cos(np.linspace(0.0, np.pi, fade))
        start = samples(at) if at > 0 else 0
        end = start + len(x)
        if end > self.send.shape[0]:
            grow = end - self.send.shape[0]
            self.dry = np.concatenate([self.dry, np.zeros((2, grow))], axis=1)
            self.send = np.concatenate([self.send, np.zeros(grow)])
        pan = np.asarray(pan, dtype=np.float64)
        if pan.ndim:
            # A moving pan shorter than the sound stays where it ended.
            pan = np.concatenate([pan[:len(x)], np.full(max(0, len(x) - len(pan)), pan[-1])])
        angle = pan * np.pi / 4.0 + np.pi / 4.0
        self.dry[0, start:end] += np.cos(angle) * x
        self.dry[1, start:end] += np.sin(angle) * x
        self.send[start:end] += send * x

    def render(self, rng, decay, wet, predelay=0.012, dark=1.2, low_pass=9500.0):
        answer = room(rng, decay, predelay, dark)
        n = self.send.shape[0]
        out = np.zeros((2, n + answer.shape[1]))
        out[:, :n] += self.dry
        for ch in range(2):
            tail = convolve(self.send, answer[ch])
            out[ch, :len(tail)] += wet * tail
            out[ch] = one_pole(one_pole(out[ch], 40.0, high=True), low_pass)
        return out


# ---------------------------------------------------------------- the sounds

def move(rng):
    mix = Mix()
    mix.add(glass(hz('D6'), 0.016, 0.12, attack=0.0009, drop=0.06, drop_time=0.006, partials=SOFT + ((3.9, 0.04, 0.12),)))
    mix.add(warm(hz('D5'), 0.02, attack=0.0012, seconds=0.12), gain=0.28)
    t = times(0.004)
    mix.add(noise(rng, 0.004, 2500, 9000) * envelope(t, 0.0004, 0.0008), gain=0.05)
    return mix.render(rng, 0.35, 0.22, predelay=0.006)


def tab(rng):
    mix = Mix()
    length = 0.42
    t = times(length)
    air = swept_noise(rng, length, lambda s: 700.0 * (3.7 ** min(1.0, s / 0.15) ** 0.8), octaves=0.9)
    shape = smooth(t / 0.06) * np.exp(-np.clip(t - 0.08, 0.0, None) / 0.055)
    sweep = -0.4 + 0.8 * smooth(t / 0.15)
    mix.add(air * shape, pan=sweep, gain=0.17)
    mix.add(glide(hz('D5'), hz('A5'), 0.12, 0.06, attack=0.02, hold=0.04, second=0.05), pan=sweep * 0.5, gain=0.12)
    mix.add(glass(hz('F#6'), 0.05, partials=SOFT, drop=0.02), at=0.08, pan=0.3, gain=0.2)
    mix.add(glass(hz('B6'), 0.045, partials=SOFT, drop=0.02), at=0.09, pan=0.4, gain=0.11)
    return mix.render(rng, 0.5, 0.3, predelay=0.01)


def confirm(rng):
    mix = Mix()
    mix.add(glass(hz('D6'), 0.1), pan=-0.12, gain=0.55)
    mix.add(glass(hz('A6'), 0.13), at=0.05, pan=0.12, gain=0.5)
    mix.add(warm(hz('D5'), 0.08, attack=0.005), gain=0.32)
    t = times(0.2)
    mix.add(noise(rng, 0.2, 2000, 8000) * envelope(t, 0.012, 0.035), gain=0.035)
    return mix.render(rng, 0.75, 0.35, predelay=0.012)


def back(rng):
    mix = Mix()
    mix.add(glass(hz('A5'), 0.085, partials=SOFT, drop=0.025), pan=0.12, gain=0.5)
    mix.add(glass(hz('E5'), 0.11, partials=SOFT, drop=0.025), at=0.055, pan=-0.12, gain=0.5)
    mix.add(warm(hz('A4'), 0.07, attack=0.005), gain=0.26)
    return mix.render(rng, 0.6, 0.3, predelay=0.012, dark=1.3, low_pass=7000.0)


def popup(rng):
    mix = Mix()
    mix.add(glide(hz('A4'), hz('A5'), 0.09, 0.07, attack=0.006, hold=0.04), gain=0.42)
    mix.add(glass(hz('D6'), 0.11), at=0.07, pan=0.08, gain=0.3)
    mix.add(glass(hz('F#6'), 0.08), at=0.075, pan=-0.08, gain=0.12)
    length = 0.25
    t = times(length)
    air = swept_noise(rng, length, lambda s: 1200.0 * 2.5 ** min(1.0, s / 0.08), octaves=1.0)
    mix.add(air * smooth(t / 0.04) * np.exp(-np.clip(t - 0.05, 0.0, None) / 0.025), gain=0.05)
    return mix.render(rng, 0.6, 0.32, predelay=0.012)


def popup_back(rng):
    mix = Mix()
    mix.add(glide(hz('A5'), hz('A4'), 0.08, 0.055, attack=0.004, hold=0.03), gain=0.42)
    mix.add(glass(hz('D5'), 0.06, partials=SOFT, drop=0.02), at=0.05, gain=0.18)
    length = 0.22
    t = times(length)
    air = swept_noise(rng, length, lambda s: 3000.0 / 2.5 ** min(1.0, s / 0.07), octaves=1.0)
    mix.add(air * smooth(t / 0.03) * np.exp(-np.clip(t - 0.04, 0.0, None) / 0.02), gain=0.04)
    return mix.render(rng, 0.5, 0.28, predelay=0.01, dark=1.3, low_pass=7500.0)


def popup_enter(rng):
    mix = Mix()
    mix.add(glass(hz('E6'), 0.03, partials=CLEAR, drop=0.03), pan=-0.1, gain=0.3)
    mix.add(glass(hz('B6'), 0.12, partials=BELL, drop=0.015), at=0.03, pan=0.1, gain=0.5)
    mix.add(warm(hz('E5'), 0.07, attack=0.004), at=0.03, gain=0.25)
    return mix.render(rng, 0.65, 0.33, predelay=0.012)


def notify(rng):
    mix = Mix()
    mix.add(glide(700.0, 1300.0, 0.03, 0.03, attack=0.002, second=0.08), gain=0.22)
    mix.add(glass(hz('F#6'), 0.17, partials=BELL, drop=0.015), at=0.035, pan=-0.18, gain=0.42)
    mix.add(warm(hz('B4'), 0.18, attack=0.006), at=0.035, gain=0.14)
    mix.add(glass(hz('B6'), 0.24, partials=BELL, drop=0.015), at=0.135, pan=0.18, gain=0.36)
    mix.add(glass(hz('F#7'), 0.12, partials=SOFT, drop=0.01), at=0.14, pan=0.3, gain=0.07)
    return mix.render(rng, 0.8, 0.36, predelay=0.015)


def boot(rng):
    total = 3.4
    mix = Mix()
    t = times(total)
    peak, fall = 1.2, 1.45
    voices = (('D3', 0.30, 0.00, -0.35), ('A3', 0.26, 0.06, 0.35), ('E4', 0.20, 0.12, -0.2), ('F#4', 0.17, 0.18, 0.2),
              ('A4', 0.16, 0.24, -0.1), ('C#5', 0.11, 0.30, 0.1), ('E5', 0.09, 0.36, 0.0))
    for note, level, entry, pan in voices:
        f = hz(note)
        rise = smooth((t - entry) / (peak - entry))
        env = rise * np.exp(-np.clip(t - fall, 0.0, None) / 0.6) * (1.0 - smooth((t - (total - 0.8)) / 0.8))
        bright = smooth(t / peak)
        tone = 0.5 * sine(np.full(len(t), f * 2 ** (3 / 1200))) + 0.5 * sine(np.full(len(t), f * 2 ** (-3 / 1200)))
        tone += bright * (0.08 * sine(np.full(len(t), 2 * f)) + 0.03 * sine(np.full(len(t), 3 * f)))
        mix.add(tone * env, pan=pan, gain=level)
    sparkles = ('D6', 'E6', 'F#6', 'A6', 'B6', 'D7', 'E7', 'F#7')
    pans = (-0.1, 0.15, -0.25, 0.3, -0.4, 0.45, -0.55, 0.6)
    for i, (note, pan) in enumerate(zip(sparkles, pans)):
        mix.add(glass(hz(note), 0.3, partials=BELL, drop=0.012), at=0.5 + i * 0.09, pan=pan, gain=0.16 - 0.009 * i)
    mix.add(glass(hz('D6'), 0.45, partials=BELL, drop=0.01), at=1.42, pan=-0.15, gain=0.22)
    mix.add(glass(hz('A6'), 0.5, partials=BELL, drop=0.01), at=1.42, pan=0.15, gain=0.18)
    mix.add(glass(hz('D7'), 0.3, partials=SOFT, drop=0.01), at=1.5, pan=0.35, gain=0.06)
    mix.add(warm(hz('D5'), 0.55, attack=0.01), at=1.42, gain=0.18)
    mix.add(warm(hz('D4'), 0.65, attack=0.02), at=1.42, gain=0.15)
    breath = noise(rng, total, 1000, 5000) * smooth(t / 1.2) * np.exp(-np.clip(t - 1.3, 0.0, None) / 0.4)
    mix.add(breath, gain=0.025)
    return mix.render(rng, 1.8, 0.42, predelay=0.02, low_pass=9000.0)


SOUNDS = {
    'move': move, 'tab': tab, 'confirm': confirm, 'back': back, 'popup': popup, 'popup_back': popup_back,
    'popup_enter': popup_enter, 'notify': notify, 'boot': boot,
}


# ---------------------------------------------------------------- loudness, files

def k_weighted(x):
    """The signal as loudness meters hear it (a rise above 1.5 kHz, less of the lows)."""
    n = len(x)
    freqs = np.fft.rfftfreq(n, 1.0 / SR)
    shelf = (10 ** (4.0 / 20)) ** smooth(np.log2(np.maximum(freqs, 1.0) / 700.0) / 2.0)
    high_pass = 1.0 / np.sqrt(1.0 + (60.0 / np.maximum(freqs, 1.0)) ** 4)
    return np.fft.irfft(np.fft.rfft(x) * shelf * high_pass, n)


def loudness(stereo):
    """The loudest 50 ms, K-weighted, in dB of full scale as if mono (a centred sound counts as its mono level)."""
    mid = k_weighted((stereo[0] + stereo[1]) / np.sqrt(2.0))
    window = samples(0.05)
    sums = np.concatenate([[0.0], np.cumsum(mid ** 2)])
    starts = np.arange(0, max(1, len(mid) - window), samples(0.005))
    rms = np.sqrt((sums[starts + window] - sums[starts]) / window)
    return 20.0 * np.log10(np.max(rms) + 1e-12)


def finish(stereo, target):
    """Trimmed where it has faded to nothing, faded out, and set to its loudness (never past the peak limit)."""
    level = np.max(np.abs(stereo), axis=0)
    loud = np.nonzero(level > np.max(level) * 10 ** (-70 / 20))[0]
    stereo = stereo[:, :loud[-1] + 1 + samples(0.01)]
    fade = min(stereo.shape[1] // 4, samples(0.03))
    stereo[:, -fade:] *= np.linspace(1.0, 0.0, fade) ** 2
    gain = 10 ** ((target - loudness(stereo)) / 20)
    peak = np.max(np.abs(stereo)) * gain
    limit = 10 ** (PEAK_LIMIT_DB / 20)
    if peak > limit:
        print(f'  quieter by {20 * np.log10(peak / limit):.1f} dB to stay under the peak limit', file=sys.stderr)
        gain *= limit / peak
    return stereo * gain


def write_wav(path, stereo):
    data = np.clip(np.round(stereo.T * 32767.0), -32768, 32767).astype('<i2')
    with wave.open(path, 'wb') as out:
        out.setnchannels(2)
        out.setsampwidth(2)
        out.setframerate(SR)
        out.writeframes(data.tobytes())


def main():
    parser = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    parser.add_argument('--wav', help='a folder to keep the WAV files in as well')
    parser.add_argument('--only', nargs='*', help='only these sounds')
    args = parser.parse_args()
    if shutil.which('ffmpeg') is None:
        sys.exit('ffmpeg is needed to write the OGG files')
    os.makedirs(OUT, exist_ok=True)
    keep = args.wav
    if keep:
        os.makedirs(keep, exist_ok=True)
    with tempfile.TemporaryDirectory() as temp:
        for index, (name, make) in enumerate(SOUNDS.items()):
            if args.only and name not in args.only:
                continue
            rng = np.random.default_rng(1000 + index)
            stereo = finish(make(rng), LOUDNESS[name])
            wav = os.path.join(keep or temp, name + '.wav')
            write_wav(wav, stereo)
            ogg = os.path.join(OUT, name + '.ogg')
            subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', wav, '-c:a', 'libvorbis', '-q:a', '6', '-map_metadata', '-1', ogg], check=True)
            print(f'{name:12s} {stereo.shape[1] / SR:5.2f} s  peak {20 * np.log10(np.max(np.abs(stereo))):6.1f} dBFS  loudness {loudness(stereo):6.1f}')


if __name__ == '__main__':
    main()
