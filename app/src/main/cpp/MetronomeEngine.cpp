#include "MetronomeEngine.h"
#include <android/log.h>
#include <cmath>

#define LOG_TAG "MetronomeEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

const float PI = 3.14159265359f;

MetronomeEngine::MetronomeEngine() {
    LOGD("MetronomeEngine created");
    regenerateClicks(mLastSampleRate);
}

MetronomeEngine::~MetronomeEngine() {
    LOGD("MetronomeEngine destroyed");
}

void MetronomeEngine::setBpm(int bpm) {
    if (bpm < 40) bpm = 40;
    if (bpm > 300) bpm = 300;
    mBpm = bpm;
    LOGD("BPM set to %d", bpm);
}

void MetronomeEngine::setStrongFreq(float freq) {
    if (freq < 200) freq = 200;
    if (freq > 3000) freq = 3000;
    mStrongFreq = freq;
    LOGD("Strong freq set to %.0f Hz", freq);
    regenerateClicks(mLastSampleRate);
}

void MetronomeEngine::setWeakFreq(float freq) {
    if (freq < 200) freq = 200;
    if (freq > 3000) freq = 3000;
    mWeakFreq = freq;
    LOGD("Weak freq set to %.0f Hz", freq);
    regenerateClicks(mLastSampleRate);
}

void MetronomeEngine::setVolume(float volume) {
    if (volume < 0) volume = 0;
    if (volume > 1) volume = 1;
    mVolume = volume;
    LOGD("Volume set to %.2f", volume);
}

void MetronomeEngine::setChannelMode(int mode) {
    if (mode < 0) mode = 0;
    if (mode > 2) mode = 2;
    mChannelMode = mode;
    LOGD("Channel mode set to %d", mode);
}

void MetronomeEngine::setWaveform(int type) {
    if (type < 0) type = 0;
    if (type > 2) type = 2;
    mWaveform = type;
    LOGD("Waveform set to %d", type);
    regenerateClicks(mLastSampleRate);
}

void MetronomeEngine::setClickDuration(int ms) {
    if (ms != 10 && ms != 15 && ms != 25) ms = 15; // разреши 10,15,25
    mClickDurationMs = ms;
    LOGD("Click duration set to %d ms", ms);
    regenerateClicks(mLastSampleRate);
}

void MetronomeEngine::setEnabled(bool enabled) {
    mEnabled = enabled;
    LOGD("Enabled set to %d", enabled);
}

void MetronomeEngine::setHolding(bool holding) {
    mHolding = holding;
    LOGD("Holding set to %d", holding);
}

void MetronomeEngine::reset() {
    mResetRequested = true;
    LOGD("Reset requested");
}

float MetronomeEngine::generateWaveform(float phase, int type) {
    switch (type) {
        case 0: // Sine
            return sinf(phase * 2.0f * PI);
        case 1: // Triangle
            phase = fmod(phase, 1.0f);
            if (phase < 0.25f)
                return 4.0f * phase;
            else if (phase < 0.75f)
                return 2.0f - 4.0f * phase;
            else
                return 4.0f * phase - 4.0f;
        case 2: // Sawtooth
            return 2.0f * (fmod(phase, 1.0f)) - 1.0f;
        default:
            return sinf(phase * 2.0f * PI);
    }
}

void MetronomeEngine::regenerateClicks(int sampleRate) {
    mLastSampleRate = sampleRate;
    mClickSamples = sampleRate * mClickDurationMs / 1000;

    mStrongClick.resize(mClickSamples);
    mWeakClick.resize(mClickSamples);

    float strongFreq = mStrongFreq;
    float weakFreq = mWeakFreq;
    int waveform = mWaveform;

    for (int i = 0; i < mClickSamples; i++) {
        float t = (float)i / sampleRate;
        float envelope = 1.0f - (float)i / mClickSamples; // линейное затухание

        // Strong click
        float phase = fmod(strongFreq * t, 1.0f);
        float value = generateWaveform(phase, waveform);
        mStrongClick[i] = value * envelope;

        // Weak click
        phase = fmod(weakFreq * t, 1.0f);
        value = generateWaveform(phase, waveform);
        mWeakClick[i] = value * envelope;
    }

    LOGD("Clicks regenerated: %d samples, strong=%.0f Hz, weak=%.0f Hz, waveform=%d",
         mClickSamples, strongFreq, weakFreq, waveform);
}

void MetronomeEngine::generate(float* left, float* right, int numFrames, int sampleRate) {
    // Если метроном выключен или холд - тишина
    if (!mEnabled || mHolding) {
        for (int i = 0; i < numFrames; i++) {
            left[i] = 0.0f;
            right[i] = 0.0f;
        }
        return;
    }

    // Если сэмпл рейт изменился - перегенерируем клики
    if (sampleRate != mLastSampleRate) {
        regenerateClicks(sampleRate);
    }

    // Сброс если запрошен
    if (mResetRequested) {
        mSamplesSinceLastBeat = 0;
        mBeatCounter = 0;
        mIsClicking = false;
        mClickPosition = 0;
        mResetRequested = false;
        LOGD("Metronome reset");
    }

    int samplesPerBeat = (int)((float)sampleRate * 60.0f / mBpm);
    float volume = mVolume;
    int channelMode = mChannelMode;

    for (int i = 0; i < numFrames; i++) {
        float leftSample = 0.0f;
        float rightSample = 0.0f;

        if (mIsClicking) {
            // Играем клик
            if (mClickPosition < mClickSamples) {
                float clickValue;
                if (mBeatCounter % 4 == 0) { // strong на первой доле
                    clickValue = mStrongClick[mClickPosition];
                } else {
                    clickValue = mWeakClick[mClickPosition];
                }
                clickValue *= volume;

                // Распределяем по каналам
                if (channelMode == 0) { // только левый
                    leftSample = clickValue;
                } else if (channelMode == 1) { // только правый
                    rightSample = clickValue;
                } else { // оба
                    leftSample = clickValue;
                    rightSample = clickValue;
                }

                mClickPosition++;
            } else {
                mIsClicking = false;
            }
        }

        if (!mIsClicking) {
            mSamplesSinceLastBeat++;

            if (mSamplesSinceLastBeat >= samplesPerBeat) {
                mSamplesSinceLastBeat = 0;
                mIsClicking = true;
                mClickPosition = 0;
                mBeatCounter++;
            }
        }

        left[i] = leftSample;
        right[i] = rightSample;
    }
}