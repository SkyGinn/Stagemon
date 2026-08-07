#ifndef METRONOME_ENGINE_H
#define METRONOME_ENGINE_H

#include <atomic>
#include <vector>
#include <cmath>

class MetronomeEngine {
public:
    MetronomeEngine();
    ~MetronomeEngine();

    // Настройки
    void setBpm(int bpm);
    void setStrongFreq(float freq);
    void setWeakFreq(float freq);
    void setVolume(float volume);
    void setChannelMode(int mode); // 0-left, 1-right, 2-both
    void setWaveform(int type);    // 0-sine, 1-triangle, 2-saw
    void setClickDuration(int ms); // 20 или 50
    void setEnabled(bool enabled);
    void setHolding(bool holding);
    void reset();

    // Генерация звука (вызывается из OboeEngine)
    void generate(float* left, float* right, int numFrames, int sampleRate);

private:
    // Прегенерация кликов при изменении параметров
    void regenerateClicks(int sampleRate);

    // Генерация одной формы волны
    float generateWaveform(float phase, int type);

    // Параметры
    std::atomic<int> mBpm{120};
    std::atomic<float> mStrongFreq{1000.0f};
    std::atomic<float> mWeakFreq{800.0f};
    std::atomic<float> mVolume{0.5f};
    std::atomic<int> mChannelMode{2}; // both по умолчанию
    std::atomic<int> mWaveform{0};    // sine по умолчанию
    std::atomic<int> mClickDurationMs{20}; // 20ms по умолчанию
    std::atomic<bool> mEnabled{false};
    std::atomic<bool> mHolding{false};
    std::atomic<bool> mResetRequested{false};

    // Прегенерированные клики
    std::vector<float> mStrongClick;
    std::vector<float> mWeakClick;
    int mClickSamples = 0;

    // Состояние
    int mSamplesSinceLastBeat = 0;
    int mBeatCounter = 0;
    bool mIsClicking = false;
    int mClickPosition = 0;
    int mLastSampleRate = 48000;
};

#endif