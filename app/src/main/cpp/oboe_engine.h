#ifndef OBOE_ENGINE_H
#define OBOE_ENGINE_H
#include <android/log.h>
#include "MetronomeEngine.h"
#define LOG_TAG "OboeEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#include <oboe/Oboe.h>
#include <mutex>
#include <atomic>
#include <string>
#include <vector>
#include <cmath>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaCodec.h>

class OboeEngine : public oboe::AudioStreamDataCallback {
public:
    OboeEngine();
    ~OboeEngine();
    float mRampStep;
    float mCurrentRamp;
    bool mRampingUp;
    bool mRampingDown;
    bool start();
    void stop();
    bool isPlaying() const { return mIsPlaying.load(); }
    uint16_t mFohFormatTag;
    uint16_t mMonFormatTag;
    uint16_t mFohBitsPerSample;
    uint16_t mMonBitsPerSample;
    int64_t getCurrentPosition() const {
        if (mFohFd != -1) {
            off_t pos = lseek(mFohFd, 0, SEEK_CUR);
            return pos > 0 ? pos : 0;
        }
        return 0;
    }
    int64_t getFohLength() const { return mFohTotalLength; }
    int32_t mFohSampleRate = 48000;
    int32_t mMonSampleRate = 48000;
    bool checkFormatSupport(int deviceId, int sampleRate, oboe::AudioFormat format);
    bool mFohRawDirect = false;
    bool mMonRawDirect = false;
    int32_t getFohSampleRate() const { return mFohSampleRate; }
    uint16_t getFohBitDepth() const { return mFohBitsPerSample; }
    int32_t getMonSampleRate() const { return mMonSampleRate; }
    uint16_t getMonBitDepth() const { return mMonBitsPerSample; }
    void setMetronomeWaveform(int type);
    void setMetronomeClickDuration(int ms);
    bool openStream(int preferredFormatIndex = 1);
    void closeStream();
    void resetTrackPositions();
    void cleanupExtractors();
    bool getNextSample(float &l, float &r, bool isFoh);
    bool isStreamOpen() const { return mStream != nullptr; }
    void setVolumes(float volFoh, float volMon);
    void setPosition(int64_t position);
    std::string getAudioDeviceInfo() const;
    void setDeviceId(int id);
    void setPairsSwap(bool swap);
    void setTrackFdsSeek(int fohFd, int64_t fohDataLength, int monFd, int64_t monDataLength);
    void setTrackDataInfo(int64_t fohDataOffset, int fohBlockAlign, int64_t monDataOffset, int monBlockAlign);
    void setChannelRouting(int fohL, int fohR, int monL, int monR);
    void resetVuLevels();
    float getCurrentFohLeft() const { return mCurrentFohLeft; }
    float getCurrentFohRight() const { return mCurrentFohRight; }
    float getCurrentMonLeft() const { return mCurrentMonLeft; }
    float getCurrentMonRight() const { return mCurrentMonRight; }
    void setMetronomeEnabled(bool enabled) { mMetronomeEnabled = enabled; }
    void setMetronomeBpm(int bpm) { mMetronomeBpm = bpm; }
    void setMetronomeVolume(float volume) { mMetronomeVolume = volume; }
    void setMetronomeChannel(int mode) { mMetronomeChannelMode = mode; }
    void setMetronomeHolding(bool holding) { mMetronomeHolding = holding; }
    void resetMetronome() { mMetronomeResetRequested = true; mMetronomeHolding = false; }
    void setMetronomeStrongFreq(int freq) { mMetronomeStrongFreq = freq; }
    void setMetronomeWeakFreq(int freq) { mMetronomeWeakFreq = freq; }
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) override;

private:
    std::shared_ptr<oboe::AudioStream> mStream;
    std::mutex mLock;
    std::atomic<bool> mIsPlaying{false};
    std::atomic<int> mCurrentSampleRate{0};
    int mChannelCount = 0;
    int mDeviceId = -1;
    std::string mAudioDeviceName;
    bool mSeekRequested = false;
    int64_t mSeekPosition = 0;
    std::atomic<bool> mSeekInProgress{false};
    int mSeekFadeFrames = 0;
    bool mSwapPairs = false;
    float mVolFoh = 1.0f;
    float mVolMon = 1.0f;
    float mCurrentFohLeft = 0.0f;
    float mCurrentFohRight = 0.0f;
    float mCurrentMonLeft = 0.0f;
    float mCurrentMonRight = 0.0f;
    MetronomeEngine mMetronome;
    AMediaExtractor* mExtractorFoh = nullptr;
    AMediaExtractor* mExtractorMon = nullptr;
    AMediaCodec* mCodecFoh = nullptr;
    AMediaCodec* mCodecMon = nullptr;
    int mFohFd = -1;
    int mMonFd = -1;
    std::vector<float> mFohPcmBuffer;
    size_t mFohBufferPos = 0;
    bool mFohInputEos = false;
    bool mFohOutputEos = false;
    std::vector<float> mMonPcmBuffer;
    size_t mMonBufferPos = 0;
    bool mMonInputEos = false;
    bool mMonOutputEos = false;
    oboe::AudioFormat mFormat = oboe::AudioFormat::Unspecified;
    int64_t mFohTotalLength = 0;
    int64_t mFohDataOffset = 44;
    int64_t mMonDataOffset = 44;
    uint16_t mFohBlockAlign = 4;
    uint16_t mMonBlockAlign = 4;
    int mFohLeftChannel = 0;
    int mFohRightChannel = 1;
    int mMonLeftChannel = 2;
    int mMonRightChannel = 3;
    std::atomic<bool> mMetronomeEnabled{false};
    std::atomic<int> mMetronomeBpm{120};
    std::atomic<float> mMetronomeVolume{0.5f};
    std::atomic<int> mMetronomeChannelMode{2};
    std::atomic<bool> mMetronomeHolding{false};
    std::atomic<bool> mMetronomeResetRequested{false};
    std::atomic<int> mMetronomeStrongFreq{1000};
    std::atomic<int> mMetronomeWeakFreq{800};
    float mMetronomePhase = 0.0f;
    int mBeatCounter = 0;
    static constexpr int CLICK_DURATION_FRAMES = 2400;
    int mClickRemainingFrames = 0;
    void generateMetronomeSamples(float *left, float *right, int numFrames);
};
#endif