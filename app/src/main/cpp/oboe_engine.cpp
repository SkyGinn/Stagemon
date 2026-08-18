#include "oboe_engine.h"
#include <jni.h>
#include <android/log.h>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaFormat.h>
#include <media/NdkMediaCodec.h>
#include <cstring>
#include <unistd.h>
#include <vector>
#include <cmath>
#include <algorithm>
#include <cstdlib>  // aligned_alloc / free

#define LOG_TAG "OboeEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

OboeEngine::OboeEngine() {
    LOGD("OboeEngine создан");
    mAudioDeviceName = "Не инициализировано";
    mDeviceId = -1;
    mChannelCount = 0;
    mSwapPairs = false;
    mVolFoh = 1.0f;
    mVolMon = 1.0f;
    mFohFormatTag = 0;
    mMonFormatTag = 0;
    mFohBitsPerSample = 0;
    mMonBitsPerSample = 0;
    mFohBlockAlign = 4;
    mMonBlockAlign = 4;
    mExtractorFoh = nullptr;
    mExtractorMon = nullptr;
    mCodecFoh = nullptr;
    mCodecMon = nullptr;
    mFohFd = -1;
    mMonFd = -1;
    mFohRawDirect = false;
    mMonRawDirect = false;
    mFohBufferPos = 0;
    mMonBufferPos = 0;
    mFohInputEos = false;
    mFohOutputEos = false;
    mMonInputEos = false;
    mMonOutputEos = false;
    mFormat = oboe::AudioFormat::Unspecified;
    mFohSampleRate = 48000;
    mMonSampleRate = 48000;
    mFohLeftChannel = 0;
    mMonLeftChannel = 2;
    mCurrentFohLeft = 0.0f;
    mCurrentFohRight = 0.0f;
    mCurrentMonLeft = 0.0f;
    mCurrentMonRight = 0.0f;

    // Ramp 500 мс
    mRampStep = 1.0f / 24000.0f;
    mCurrentRamp = 0.0f;
    mRampingUp = false;
    mRampingDown = false;
    mIsPlaying = false;
}

OboeEngine::~OboeEngine() {
    stop();
    cleanupExtractors();
    LOGD("OboeEngine уничтожен");
}

void OboeEngine::cleanupExtractors() {
    if (mCodecFoh) {
        AMediaCodec_stop(mCodecFoh);
        AMediaCodec_delete(mCodecFoh);
        mCodecFoh = nullptr;
    }
    if (mCodecMon) {
        AMediaCodec_stop(mCodecMon);
        AMediaCodec_delete(mCodecMon);
        mCodecMon = nullptr;
    }
    if (mExtractorFoh) {
        AMediaExtractor_delete(mExtractorFoh);
        mExtractorFoh = nullptr;
    }
    if (mExtractorMon) {
        AMediaExtractor_delete(mExtractorMon);
        mExtractorMon = nullptr;
    }
    mFohFd = -1;
    mMonFd = -1;

    mFohRawDirect = false;
    mMonRawDirect = false;
    mFohPcmBuffer.clear();
    mMonPcmBuffer.clear();
    mFohBufferPos = 0;
    mMonBufferPos = 0;
    mFohInputEos = false;
    mFohOutputEos = false;
    mMonInputEos = false;
    mMonOutputEos = false;
}

void OboeEngine::resetTrackPositions() {
    // ТОЛЬКО если нет offset (начинаем с начала)
    if (mFohRawDirect && mFohFd != -1) {
        // Проверяем текущую позицию
        off_t currentPos = lseek(mFohFd, 0, SEEK_CUR);
        if (currentPos <= mFohDataOffset) {
            lseek(mFohFd, mFohDataOffset, SEEK_SET);
            LOGD("FOH позиция сброшена в начало");
        } else {
            LOGD("FOH сохраняем позицию: %ld", currentPos);
        }
    }
    if (mMonRawDirect && mMonFd != -1) {
        off_t currentPos = lseek(mMonFd, 0, SEEK_CUR);
        if (currentPos <= mMonDataOffset) {
            lseek(mMonFd, mMonDataOffset, SEEK_SET);
            LOGD("MON позиция сброшена в начало");
        } else {
            LOGD("MON сохраняем позицию: %ld", currentPos);
        }
    }
    mFohPcmBuffer.clear();
    mMonPcmBuffer.clear();
    mFohBufferPos = 0;
    mMonBufferPos = 0;
    mFohOutputEos = false;
    mMonOutputEos = false;
}

void OboeEngine::setDeviceId(int id) {
    mDeviceId = id;
}

void OboeEngine::setPairsSwap(bool swap) {
    mSwapPairs = swap;
}

void OboeEngine::setVolumes(float volFoh, float volMon) {
    mVolFoh = volFoh;
    mVolMon = volMon;
}

void OboeEngine::setChannelRouting(int fohL, int fohR, int monL, int monR) {
    LOGD("setChannelRouting CALLED with fohL=%d, fohR=%d, monL=%d, monR=%d", fohL, fohR, monL, monR);
    mFohLeftChannel = fohL;
    mFohRightChannel = fohR;
    mMonLeftChannel = monL;
    mMonRightChannel = monR;
    LOGD("Маршрутизация: FOH L=%d, FOH R=%d, MON L=%d, MON R=%d", fohL, fohR, monL, monR);
}


void OboeEngine::setMetronomeWaveform(int type) {
    mMetronome.setWaveform(type);
}

void OboeEngine::setMetronomeClickDuration(int ms) {
    mMetronome.setClickDuration(ms);
}
void OboeEngine::setTrackDataInfo(int64_t fohDataOffset, int fohBlockAlign, int64_t monDataOffset, int monBlockAlign) {
    mFohDataOffset = fohDataOffset > 0 ? fohDataOffset : 44;
    mMonDataOffset = monDataOffset > 0 ? monDataOffset : 44;
    mFohBlockAlign = fohBlockAlign > 0 ? static_cast<uint16_t>(fohBlockAlign) : 4;
    mMonBlockAlign = monBlockAlign > 0 ? static_cast<uint16_t>(monBlockAlign) : 4;
    LOGD("setTrackDataInfo: FOH offset=%lld blockAlign=%u, MON offset=%lld blockAlign=%u",
         (long long)mFohDataOffset, mFohBlockAlign,
         (long long)mMonDataOffset, mMonBlockAlign);
}

void OboeEngine::setTrackFdsSeek(int fohFd, int64_t fohDataLength, int monFd, int64_t monDataLength) {
    cleanupExtractors();
    mFohFd = fohFd;
    mMonFd = monFd;
    mFohRawDirect = (mFohFd >= 0);
    mMonRawDirect = (mMonFd >= 0);
    mFohTotalLength = fohDataLength;

    LOGD("setTrackFdsSeek: FOH fd=%d len=%lld offset=%lld bps=%u fmt=%u, MON fd=%d len=%lld offset=%lld bps=%u fmt=%u",
         mFohFd, (long long)fohDataLength, (long long)mFohDataOffset, mFohBitsPerSample, mFohFormatTag,
         mMonFd, (long long)monDataLength, (long long)mMonDataOffset, mMonBitsPerSample, mMonFormatTag);
}

bool OboeEngine::openStream(int preferredFormatIndex) {
    try {
        if (mStream) {
            mStream->close();
            mStream.reset();
        }

        LOGD("Открываем стрим 48000 Гц, устройство=%d, formatPref=%d", mDeviceId, preferredFormatIndex);

        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output);
        builder.setSharingMode(oboe::SharingMode::Exclusive);
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
        builder.setSampleRate(48000);
        builder.setChannelCount(4);
        builder.setUsage(oboe::Usage::Game);
        builder.setContentType(oboe::ContentType::Music);
        builder.setBufferCapacityInFrames(16384);
        builder.setFramesPerCallback(2400);
        builder.setDataCallback(static_cast<oboe::AudioStreamDataCallback*>(this));
        builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::None);
        builder.setFormatConversionAllowed(true);
        builder.setChannelConversionAllowed(false);

        if (mDeviceId >= 0) builder.setDeviceId(mDeviceId);

        std::vector<oboe::AudioFormat> preferredFormats;
        switch (preferredFormatIndex) {
            case 0:
                preferredFormats = {
                        oboe::AudioFormat::I16,
                        oboe::AudioFormat::I24,
                        oboe::AudioFormat::I32,
                        oboe::AudioFormat::Float
                };
                break;
            case 2:
                preferredFormats = {
                        oboe::AudioFormat::Float,
                        oboe::AudioFormat::I32,
                        oboe::AudioFormat::I24,
                        oboe::AudioFormat::I16
                };
                break;
            case 1:
            default:
                preferredFormats = {
                        oboe::AudioFormat::I24,
                        oboe::AudioFormat::I32,
                        oboe::AudioFormat::Float,
                        oboe::AudioFormat::I16
                };
                break;
        }

        oboe::Result result = oboe::Result::ErrorUnimplemented;
        for (auto format : preferredFormats) {
            builder.setFormat(format);
            result = builder.openStream(mStream);
            if (result == oboe::Result::OK) {
                mFormat = format;
                LOGD("✅ Stream opened with format: %d", static_cast<int>(format));
                LOGD("   Actual stream format: %d, sampleRate: %d, channelCount: %d",
                     static_cast<int>(mStream->getFormat()),
                     mStream->getSampleRate(),
                     mStream->getChannelCount());
                break;
            }
        }
        // =================================================================

        if (result != oboe::Result::OK || !mStream) {
            LOGE("openStream failed: %d", result);
            mAudioDeviceName = "❌ Не удалось открыть стрим";
            return false;
        }



        mChannelCount = mStream->getChannelCount();
        mCurrentSampleRate = mStream->getSampleRate();

        LOGD("Финальный формат: %d", static_cast<int>(mFormat));

        char buf[512];
        snprintf(buf, sizeof(buf),
                 "USB Device ID: %d",
                 mStream->getDeviceId());
        mAudioDeviceName = buf;

        LOGD("Стрим открыт");
        return true;
    } catch (...) {
        mAudioDeviceName = "❌ Исключение";
        return false;
    }
}

void OboeEngine::closeStream() {
    if (mStream) {
        mStream->close();
        mStream.reset();
    }
}

bool OboeEngine::start() {
    std::lock_guard<std::mutex> lock(mLock);
    if (mIsPlaying) return true;

    if (mDeviceId < 0) {
        mAudioDeviceName = "❌ Нет USB";
        return false;
    }

    if (!openStream(1)) return false;

    if (!mFohRawDirect || !mMonRawDirect) {
        mAudioDeviceName = "❌ Треки не загружены";
        closeStream();
        return false;
    }

    // *** ФИКС 1: Сбрасываем EOS флаги при повторном старте ***
    mFohOutputEos = false;
    mMonOutputEos = false;
    mFohInputEos = false;
    mMonInputEos = false;
    mRampingDown = false;

    resetTrackPositions();

    mRampingUp = true;
    mCurrentRamp = 0.0f;


    // ✅ ДОБАВИТЬ: ждём пока стрим будет готов
    oboe::Result r = mStream->requestStart();
    if (r != oboe::Result::OK) {
        LOGE("Start failed: %d, retrying...", r);
        // Пробуем ещё раз
        usleep(50000); // 50мс
        r = mStream->requestStart();
        if (r != oboe::Result::OK) {
            LOGE("Start failed again: %d", r);
            closeStream();
            return false;
        }
    }

    mIsPlaying = true;
    LOGD("start() завершён");
    return true;
}

void OboeEngine::stop() {
    std::lock_guard<std::mutex> lock(mLock);
    if (!mIsPlaying) return;

    mRampingDown = true;
    mCurrentRamp = 1.0f;

    if (mStream) mStream->requestStop();

    mIsPlaying = false;
    closeStream();
    LOGD("stop() завершён");
}

void OboeEngine::setPosition(int64_t positionBytes) {
    // Блокируем мьютекс, чтобы onAudioReady не мог читать данные во время перемотки
    std::lock_guard<std::mutex> lock(mLock);

    // 1. Очищаем вариспид буферы и сбрасываем их состояния
    mFohVarispeedBuf.clear();
    mMonVarispeedBuf.clear();
    mFohVarispeedBufPos = 0;
    mMonVarispeedBufPos = 0;
    mFohVarispeedPhase = 0.0f;
    mMonVarispeedPhase = 0.0f;

    // 2. Очищаем PCM буферы (если они есть в вашем коде как векторы/буферы)
    // Если у вас они просто указывают на память, то достаточно сбросить позицию
    if (!mFohPcmBuffer.empty()) mFohPcmBuffer.clear();
    if (!mMonPcmBuffer.empty()) mMonPcmBuffer.clear();

    // Сбрасываем позиции чтения
    mFohBufferPos = positionBytes;
    mMonBufferPos = positionBytes;

    // 3. Выполняем физическую перемотку файлов (lseek)
    // Так как мы захватили mLock, поток onAudioReady сейчас ждет и не читает файл
    if (mFohFd >= 0) {
        lseek(mFohFd, positionBytes, SEEK_SET);
    }
    if (mMonFd >= 0) {
        lseek(mMonFd, positionBytes, SEEK_SET);
    }

    // Лог для отладки (можно убрать потом)
    // __android_log_print(ANDROID_LOG_DEBUG, "OboeEngine", "Seek completed to: %lld", (long long)positionBytes);
}

void OboeEngine::resetVuLevels() {
    std::lock_guard<std::mutex> lock(mLock);
    mCurrentFohLeft = 0.0f;
    mCurrentFohRight = 0.0f;
    mCurrentMonLeft = 0.0f;
    mCurrentMonRight = 0.0f;
    LOGD("VU уровни сброшены в ноль");
}

std::string OboeEngine::getAudioDeviceInfo() const {
    return mAudioDeviceName;
}

bool OboeEngine::getNextSample(float &l, float &r, bool isFoh) {
    int fd = isFoh ? mFohFd : mMonFd;

    // ========== ОБЫЧНЫЙ РЕЖИМ ==========
    std::vector<float> &buf = isFoh ? mFohPcmBuffer : mMonPcmBuffer;
    size_t &pos = isFoh ? mFohBufferPos : mMonBufferPos;
    bool &outputEos = isFoh ? mFohOutputEos : mMonOutputEos;
    uint16_t bps = isFoh ? mFohBitsPerSample : mMonBitsPerSample;
    uint16_t blockAlign = isFoh ? mFohBlockAlign : mMonBlockAlign;

    if (bps == 0 || blockAlign == 0) { outputEos = true; l = r = 0.0f; return false; }

    if (pos + 1 >= buf.size()) {
        buf.clear();
        pos = 0;
        const size_t chunkFrames = 4096;
        size_t bytesPerFrame = blockAlign;
        const size_t chunkBytes = chunkFrames * bytesPerFrame;
        void *aligned_mem = aligned_alloc(16, chunkBytes);
        if (!aligned_mem) { outputEos = true; l = r = 0.0f; return false; }
        uint8_t *data = static_cast<uint8_t*>(aligned_mem);
        ssize_t n = ::read(fd, data, chunkBytes);
        if (n > 0) {
            size_t frames = n / bytesPerFrame;
            buf.resize(frames * 2);
            uint16_t fmt = isFoh ? mFohFormatTag : mMonFormatTag;
            for (size_t i = 0; i < frames; i++) {
                size_t base = i * blockAlign;
                if (bps == 16) {
                    int16_t valL = static_cast<int16_t>(data[base] | (data[base + 1] << 8));
                    buf[i * 2] = valL / 32768.0f;
                    if (bytesPerFrame >= 4) {
                        int16_t valR = static_cast<int16_t>(data[base + 2] | (data[base + 3] << 8));
                        buf[i * 2 + 1] = valR / 32768.0f;
                    } else buf[i * 2 + 1] = buf[i * 2];
                } else if (bps == 24) {
                    int32_t valL = (data[base] | (data[base + 1] << 8) | (data[base + 2] << 16));
                    if (valL & 0x800000) valL |= 0xFF000000;
                    buf[i * 2] = valL / 8388608.0f;
                    if (bytesPerFrame >= 6) {
                        int32_t valR = (data[base + 3] | (data[base + 4] << 8) | (data[base + 5] << 16));
                        if (valR & 0x800000) valR |= 0xFF000000;
                        buf[i * 2 + 1] = valR / 8388608.0f;
                    } else buf[i * 2 + 1] = buf[i * 2];
                } else if (bps == 32) {
                    if (fmt == 3) {
                        memcpy(&buf[i * 2], &data[base], sizeof(float));
                        if (bytesPerFrame >= 8) memcpy(&buf[i * 2 + 1], &data[base + 4], sizeof(float));
                        else buf[i * 2 + 1] = buf[i * 2];
                        buf[i * 2] = std::max(-1.0f, std::min(1.0f, buf[i * 2]));
                        buf[i * 2 + 1] = std::max(-1.0f, std::min(1.0f, buf[i * 2 + 1]));
                    } else {
                        int32_t valL = static_cast<int32_t>(data[base] | (data[base + 1] << 8) | (data[base + 2] << 16) | (data[base + 3] << 24));
                        buf[i * 2] = valL / 2147483648.0f;
                        if (bytesPerFrame >= 8) {
                            int32_t valR = static_cast<int32_t>(data[base + 4] | (data[base + 5] << 8) | (data[base + 6] << 16) | (data[base + 7] << 24));
                            buf[i * 2 + 1] = valR / 2147483648.0f;
                        } else buf[i * 2 + 1] = buf[i * 2];
                    }
                }
            }
        } else if (n == 0) { outputEos = true; l = r = 0.0f; free(aligned_mem); return false; }
        else { outputEos = true; l = r = 0.0f; free(aligned_mem); return false; }
        free(aligned_mem);
        if (buf.empty()) { outputEos = true; l = r = 0.0f; return false; }
    }

    l = buf[pos];
    r = buf[pos + 1];
    pos += 2;
    return true;
}

oboe::DataCallbackResult OboeEngine::onAudioReady(oboe::AudioStream *stream, void *audioData, int32_t numFrames) {
    if (!mIsPlaying || numFrames <= 0) return oboe::DataCallbackResult::Continue;

    // ========== ОБРАБОТКА SEEK ==========
    if (mSeekRequested) {
        mSeekRequested = false;
        mSeekInProgress = true;
        mSeekFadeFrames = 0;

        // Сначала отключаем varispeed, чтобы остановить чтение буферов
        mFohVarispeedActive = false;
        mMonVarispeedActive = false;

        // Очищаем PCM буферы
        mFohPcmBuffer.clear();
        mMonPcmBuffer.clear();
        mFohBufferPos = 0;
        mMonBufferPos = 0;

        // Очищаем varispeed буферы и сбрасываем их состояния
        mFohVarispeedBuf.clear();
        mMonVarispeedBuf.clear();
        mFohVarispeedBufPos = 0;
        mMonVarispeedBufPos = 0;
        mFohVarispeedPhase = 0.0f;
        mMonVarispeedPhase = 0.0f;

        mFohOutputEos = false;
        mMonOutputEos = false;
        mFohInputEos = false;
        mMonInputEos = false;

        // Устанавливаем позицию
        if (mFohFd != -1) {
            lseek(mFohFd, mSeekPosition + mFohDataOffset, SEEK_SET);
        }
        if (mMonFd != -1) {
            lseek(mMonFd, mSeekPosition + mMonDataOffset, SEEK_SET);
        }
        LOGD("Seek completed to: %ld", mSeekPosition);
    }

    if (mRampingUp) {
        mCurrentRamp += mRampStep * numFrames;
        if (mCurrentRamp > 1.0f) {
            mCurrentRamp = 1.0f;
            mRampingUp = false;
        }
    } else if (mRampingDown) {
        mCurrentRamp -= mRampStep * numFrames;
        if (mCurrentRamp < 0.0f) {
            mCurrentRamp = 0.0f;
            mRampingDown = false;
        }
    }

    float ramp = mCurrentRamp;
    int ch = stream->getChannelCount();
    int fL = mFohLeftChannel;
    int fR = mFohRightChannel;
    int mL = mMonLeftChannel;
    int mR = mMonRightChannel;

    bool bothEos = mFohOutputEos && mMonOutputEos;
    if (bothEos) {
        LOGD("Треки закончились, останавливаем");
        mIsPlaying = false;
        return oboe::DataCallbackResult::Stop;
    }

    const int FADE_FRAMES = 500;
    float currentFohLeft = 0.0f, currentFohRight = 0.0f;
    float currentMonLeft = 0.0f, currentMonRight = 0.0f;

    float tempBuffer[4800 * 4];

    for (int i = 0; i < numFrames; ++i) {
        float fl = 0.0f, fr = 0.0f, ml = 0.0f, mr = 0.0f;

        if (!mFohOutputEos) getNextSample(fl, fr, true);
        fl *= mVolFoh * ramp;
        fr *= mVolFoh * ramp;
        currentFohLeft += fabs(fl);
        currentFohRight += fabs(fr);

        if (!mMonOutputEos) getNextSample(ml, mr, false);
        ml *= mVolMon * ramp;
        mr *= mVolMon * ramp;
        currentMonLeft += fabs(ml);
        currentMonRight += fabs(mr);

        if (mSeekInProgress && mSeekFadeFrames < FADE_FRAMES) {
            float fadeFactor = (float)mSeekFadeFrames / (float)FADE_FRAMES;
            fl *= fadeFactor;
            fr *= fadeFactor;
            ml *= fadeFactor;
            mr *= fadeFactor;
            mSeekFadeFrames++;
        }

        float* frameStart = tempBuffer + i * ch;
        for (int c = 0; c < ch; c++) {
            frameStart[c] = 0.0f;
        }

        if (fL >= 0 && fL < ch) frameStart[fL] = fl;
        if (fR >= 0 && fR < ch) frameStart[fR] = fr;
        if (mL >= 0 && mL < ch) frameStart[mL] = ml;
        if (mR >= 0 && mR < ch) frameStart[mR] = mr;
    }

    mCurrentFohLeft = currentFohLeft / numFrames;
    mCurrentFohRight = currentFohRight / numFrames;
    mCurrentMonLeft = currentMonLeft / numFrames;
    mCurrentMonRight = currentMonRight / numFrames;

    oboe::AudioFormat deviceFormat = stream->getFormat();

    if (deviceFormat == oboe::AudioFormat::Float) {
        memcpy(audioData, tempBuffer, numFrames * ch * sizeof(float));
    } else if (deviceFormat == oboe::AudioFormat::I24) {
        uint8_t* output = static_cast<uint8_t*>(audioData);
        for (int i = 0; i < numFrames * ch; i++) {
            float val = tempBuffer[i];
            if (val > 1.0f) val = 1.0f;
            if (val < -1.0f) val = -1.0f;
            int32_t sample = static_cast<int32_t>(val * 8388607.0f);
            *output++ = static_cast<uint8_t>(sample & 0xFF);
            *output++ = static_cast<uint8_t>((sample >> 8) & 0xFF);
            *output++ = static_cast<uint8_t>((sample >> 16) & 0xFF);
        }
    } else if (deviceFormat == oboe::AudioFormat::I32) {
        int32_t* output = static_cast<int32_t*>(audioData);
        for (int i = 0; i < numFrames * ch; i++) {
            output[i] = static_cast<int32_t>(tempBuffer[i] * 2147483647.0f);
        }
    } else if (deviceFormat == oboe::AudioFormat::I16) {
        int16_t* output = static_cast<int16_t*>(audioData);
        for (int i = 0; i < numFrames * ch; i++) {
            output[i] = static_cast<int16_t>(tempBuffer[i] * 32767.0f);
        }
    }

    if (mSeekInProgress && mSeekFadeFrames >= FADE_FRAMES) {
        mSeekInProgress = false;
    }

    return oboe::DataCallbackResult::Continue;
}

void OboeEngine::generateMetronomeSamples(float *left, float *right, int numFrames) {
    LOGD("generateMetronomeSamples: enabled=%d, holding=%d, bpm=%d, sampleRate=%d",
         (int)mMetronomeEnabled.load(),
         (int)mMetronomeHolding.load(),
         (int)mMetronomeBpm.load(),
         (int)mCurrentSampleRate.load());

    if (!mMetronomeEnabled || mMetronomeHolding) {
        LOGD("Metronome disabled, returning silence");
        *left = 0.0f;
        *right = 0.0f;
        return;
    }

    if (mCurrentSampleRate <= 0) {
        LOGD("ERROR: Sample rate not set!");
        *left = 0.0f;
        *right = 0.0f;
        return;
    }

    float samplesPerBeat = (float)mCurrentSampleRate * 60.0f / (float)mMetronomeBpm;
    LOGD("samplesPerBeat = %f", samplesPerBeat);

    if (mMetronomeResetRequested) {
        LOGD("Resetting metronome");
        mMetronomePhase = 0.0f;
        mBeatCounter = 0;
        mClickRemainingFrames = 0;
        mMetronomeResetRequested = false;
    }

    float clickValue = 0.0f;
    float currentFreq = 0.0f;

    if (mMetronomePhase < 1.0f) {
        if (mClickRemainingFrames > 0) {
            bool isStrong = false;
            if (mBeatCounter == 0) {
                isStrong = true;
            }

            currentFreq = isStrong ? mMetronomeStrongFreq : mMetronomeWeakFreq;
            LOGD("Generating click: freq=%f, remaining=%d", currentFreq, mClickRemainingFrames);

            float t = (float)(CLICK_DURATION_FRAMES - mClickRemainingFrames) / CLICK_DURATION_FRAMES;
            clickValue = sinf(t * 2.0f * M_PI * (currentFreq / 1000.0f)) * (1.0f - t);
            mClickRemainingFrames--;
        }
    }

    mMetronomePhase += 1.0f / samplesPerBeat;

    if (mMetronomePhase >= 1.0f) {
        mMetronomePhase -= 1.0f;
        mBeatCounter++;
        mClickRemainingFrames = CLICK_DURATION_FRAMES;
        LOGD("Beat! counter=%d", mBeatCounter);

        if (mBeatCounter > 3) mBeatCounter = 0;
    }

    clickValue *= mMetronomeVolume;
    LOGD("clickValue = %f", clickValue);

    switch (mMetronomeChannelMode) {
        case 0:
            *left = clickValue;
            *right = 0.0f;
            LOGD("Output to LEFT only");
            break;
        case 1:
            *left = 0.0f;
            *right = clickValue;
            LOGD("Output to RIGHT only");
            break;
        default:
            *left = clickValue;
            *right = clickValue;
            LOGD("Output to BOTH");
            break;
    }
    LOGD("FINAL clickValue = %f, left=%f, right=%f", clickValue, *left, *right);
}

bool OboeEngine::checkFormatSupport(int deviceId, int sampleRate, oboe::AudioFormat format) {
    try {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Output);
        builder.setSharingMode(oboe::SharingMode::Exclusive);
        builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
        builder.setSampleRate(sampleRate);
        builder.setChannelCount(4);
        builder.setFormat(format);
        builder.setFormatConversionAllowed(false);       // ← НЕ РАЗРЕШАЕМ конвертацию формата
        builder.setChannelConversionAllowed(false);      // ← НЕ РАЗРЕШАЕМ конвертацию каналов
        builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::None);  // ← НЕ РАЗРЕШАЕМ SRC
        if (deviceId >= 0) builder.setDeviceId(deviceId);

        std::shared_ptr<oboe::AudioStream> tempStream;
        oboe::Result result = builder.openStream(tempStream);

        if (result == oboe::Result::OK && tempStream) {
            // ✅ ПРОВЕРЯЕМ что реальный формат стрима СОВПАДАЕТ с запрошенным
            bool formatMatch = (tempStream->getFormat() == format);
            bool sampleRateMatch = (tempStream->getSampleRate() == sampleRate);
            bool channelMatch = (tempStream->getChannelCount() == 4);
            bool isExclusive = (tempStream->getSharingMode() == oboe::SharingMode::Exclusive);

            LOGD("checkFormatSupport: requested fmt=%d sr=%d | actual fmt=%d sr=%d ch=%d exclusive=%d",
                 static_cast<int>(format), sampleRate,
                 static_cast<int>(tempStream->getFormat()),
                 tempStream->getSampleRate(),
                 tempStream->getChannelCount(),
                 isExclusive ? 1 : 0);

            tempStream->close();

            if (formatMatch && sampleRateMatch && channelMatch && isExclusive) {
                LOGD("✅ NATIVE support: fmt=%d sr=%d", static_cast<int>(format), sampleRate);
                return true;
            } else {
                LOGD("❌ NOT native (device uses conversion): fmt=%d sr=%d",
                     static_cast<int>(format), sampleRate);
                return false;
            }
        } else {
            LOGD("❌ openStream FAILED: fmt=%d sr=%d result=%d",
                 static_cast<int>(format), sampleRate, static_cast<int>(result));
            return false;
        }
    } catch (...) {
        LOGD("❌ Exception in checkFormatSupport");
        return false;
    }
}
extern "C" {

JNIEXPORT jfloatArray JNICALL
Java_com_example_stagemon_MainActivity_extractPeaks(
        JNIEnv *env, jobject thiz,
        jint fd, jlong offset, jlong data_length,
        jint bits_per_sample, jint format_tag, jint num_peaks) {

    if (fd < 0 || data_length <= 0 || num_peaks <= 0) {
        return env->NewFloatArray(0);
    }

    off_t current_pos = lseek(fd, offset, SEEK_SET);
    if (current_pos == (off_t) - 1) return env->NewFloatArray(0);

    int bytes_per_sample = bits_per_sample / 8;
    if (bytes_per_sample == 0) bytes_per_sample = 2;

    jlong total_samples = data_length / bytes_per_sample;
    jlong samples_per_peak = total_samples / num_peaks;
    if (samples_per_peak < 1) samples_per_peak = 1;

    float max_possible_val = 1.0f;
    bool is_float = (format_tag == 3); // WAVE_FORMAT_IEEE_FLOAT

    if (is_float) max_possible_val = 1.0f;
    else if (bits_per_sample == 16) max_possible_val = 32768.0f;
    else if (bits_per_sample == 24) max_possible_val = 8388608.0f;
    else if (bits_per_sample == 32) max_possible_val = 2147483648.0f;
    else max_possible_val = 32768.0f;

    // Читаем чанками, чтобы не выделять гигантские буферы в памяти
    size_t max_chunk_samples = 500000;
    size_t chunk_samples = std::min((size_t) samples_per_peak, max_chunk_samples);
    std::vector<uint8_t> buffer(chunk_samples * bytes_per_sample);
    std::vector<float> peaks(num_peaks, 0.0f);

    for (int i = 0; i < num_peaks; ++i) {
        float peak = 0.0f;
        jlong samples_to_read = samples_per_peak;

        while (samples_to_read > 0) {
            size_t to_read = std::min((size_t) samples_to_read, chunk_samples);
            ssize_t bytes_read = read(fd, buffer.data(), to_read * bytes_per_sample);
            if (bytes_read <= 0) break;

            size_t samples_read = bytes_read / bytes_per_sample;

            if (is_float && bits_per_sample == 32) {
                float *f_buf = reinterpret_cast<float *>(buffer.data());
                for (size_t s = 0; s < samples_read; ++s) {
                    float val = std::abs(f_buf[s]);
                    if (val > peak) peak = val;
                }
            } else if (bits_per_sample == 16) {
                int16_t *i_buf = reinterpret_cast<int16_t *>(buffer.data());
                for (size_t s = 0; s < samples_read; ++s) {
                    float val = std::abs((float) i_buf[s]);
                    if (val > peak) peak = val;
                }
            } else if (bits_per_sample == 24) {
                uint8_t *b_buf = buffer.data();
                for (size_t s = 0; s < samples_read; ++s) {
                    int32_t val =
                            (b_buf[s * 3]) | (b_buf[s * 3 + 1] << 8) | (b_buf[s * 3 + 2] << 16);
                    if (val & 0x800000) val |= 0xFF000000; // Sign extension
                    float abs_val = std::abs((float) val);
                    if (abs_val > peak) peak = abs_val;
                }
            } else if (bits_per_sample == 32) {
                int32_t *i_buf = reinterpret_cast<int32_t *>(buffer.data());
                for (size_t s = 0; s < samples_read; ++s) {
                    float val = std::abs((float) i_buf[s]);
                    if (val > peak) peak = val;
                }
            }

            samples_to_read -= samples_read;
            if (bytes_read < (ssize_t)(to_read * bytes_per_sample)) samples_to_read = 0;
        }
        peaks[i] = peak / max_possible_val;
        if (peaks[i] > 1.0f) peaks[i] = 1.0f;
    }

    jfloatArray result = env->NewFloatArray(num_peaks);
    if (result != nullptr) env->SetFloatArrayRegion(result, 0, num_peaks, peaks.data());
    return result;
}

JNIEXPORT jlong JNICALL Java_com_example_stagemon_MainActivity_createEngine(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new OboeEngine());
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_destroyEngine(JNIEnv *, jobject, jlong ptr) {
    delete reinterpret_cast<OboeEngine *>(ptr);
}

JNIEXPORT jboolean JNICALL
Java_com_example_stagemon_MainActivity_startEngine(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->start();
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_stopEngine(JNIEnv *, jobject, jlong ptr) {
    reinterpret_cast<OboeEngine *>(ptr)->stop();
}

JNIEXPORT jboolean JNICALL
Java_com_example_stagemon_MainActivity_isPlaying(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->isPlaying();
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_setVolumes(JNIEnv *, jobject, jlong ptr, jfloat v1,
                                                  jfloat v2) {
    reinterpret_cast<OboeEngine *>(ptr)->setVolumes(v1, v2);
}

JNIEXPORT jstring JNICALL
Java_com_example_stagemon_MainActivity_getAudioDeviceInfo(JNIEnv *env, jobject, jlong ptr) {
    std::string s = reinterpret_cast<OboeEngine *>(ptr)->getAudioDeviceInfo();
    return env->NewStringUTF(s.c_str());
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_setDeviceId(JNIEnv *, jobject, jlong ptr, jint id) {
    reinterpret_cast<OboeEngine *>(ptr)->setDeviceId(id);
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_setPairsSwap(JNIEnv *, jobject, jlong ptr, jboolean swap) {
    reinterpret_cast<OboeEngine *>(ptr)->setPairsSwap(swap);
}

JNIEXPORT jboolean JNICALL Java_com_example_stagemon_MainActivity_00024Companion_checkFormatSupport(
        JNIEnv *, jobject, jint deviceId, jint sampleRate, jint formatCode) {
    oboe::AudioFormat format;
    switch (formatCode) {
        case 1:
            format = oboe::AudioFormat::I16;
            break;
        case 2:
            format = oboe::AudioFormat::I24;
            break;
        case 3:
            format = oboe::AudioFormat::I32;
            break;
        case 4:
            format = oboe::AudioFormat::Float;
            break;
        default:
            return false;
    }

    OboeEngine *engine = new OboeEngine();
    bool result = engine->checkFormatSupport(deviceId, sampleRate, format);
    delete engine;
    return result;
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_setChannelRouting(JNIEnv *, jobject, jlong ptr, jint fohL,
                                                         jint fohR, jint monL, jint monR) {
    reinterpret_cast<OboeEngine *>(ptr)->setChannelRouting(fohL, fohR, monL, monR);
}

JNIEXPORT jfloat JNICALL
Java_com_example_stagemon_MainActivity_getFohLeft(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->getCurrentFohLeft();
}

JNIEXPORT jfloat JNICALL
Java_com_example_stagemon_MainActivity_getFohRight(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->getCurrentFohRight();
}

JNIEXPORT jfloat JNICALL
Java_com_example_stagemon_MainActivity_getMonLeft(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->getCurrentMonLeft();
}

JNIEXPORT jfloat JNICALL
Java_com_example_stagemon_MainActivity_getMonRight(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->getCurrentMonRight();
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_resetVuLevels(JNIEnv *, jobject, jlong ptr) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) {
        engine->resetVuLevels();
    }
}
JNIEXPORT jlong JNICALL
Java_com_example_stagemon_MainActivity_getCurrentPosition(JNIEnv *, jobject, jlong ptr) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) {
        return engine->getCurrentPosition();  // вызываем метод класса
    }
    return 0L;
}

JNIEXPORT jlong JNICALL
Java_com_example_stagemon_MainActivity_getFohLength(JNIEnv *, jobject, jlong ptr) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) {
        return engine->getFohLength();
    }
    return 0L;
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_setPosition(JNIEnv *, jobject, jlong ptr, jlong position) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) {
        engine->setPosition(position);
    }
}

JNIEXPORT jboolean JNICALL
Java_com_example_stagemon_MainActivity_isStreamOpen(JNIEnv *, jobject, jlong ptr) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    return engine->isStreamOpen();
}

JNIEXPORT jboolean JNICALL
Java_com_example_stagemon_MainActivity_openStream(JNIEnv *, jobject, jlong ptr, jint deviceId,
                                                  jint formatIndex) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    engine->setDeviceId(deviceId);
    return engine->openStream(formatIndex);
}

// ========== JNI ДЛЯ МЕТРОНОМА ==========
JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_setMetronomeEnabled(JNIEnv *, jobject,
                                                                          jlong ptr,
                                                                          jboolean enabled) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setMetronomeEnabled(enabled);
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_setMetronomeBpm(JNIEnv *, jobject, jlong ptr,
                                                                      jint bpm) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setMetronomeBpm(bpm);
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_setMetronomeVolume(JNIEnv *, jobject,
                                                                         jlong ptr, jfloat volume) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setMetronomeVolume(volume);
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_setMetronomeChannel(JNIEnv *, jobject,
                                                                          jlong ptr, jint mode) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setMetronomeChannel(mode);
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_setMetronomeHolding(JNIEnv *, jobject,
                                                                          jlong ptr,
                                                                          jboolean holding) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setMetronomeHolding(holding);
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_resetMetronome(JNIEnv *, jobject, jlong ptr) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->resetMetronome();
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_setMetronomeStrongFreq(JNIEnv *, jobject,
                                                                             jlong ptr, jint freq) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setMetronomeStrongFreq(freq);
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_setMetronomeWeakFreq(JNIEnv *, jobject,
                                                                           jlong ptr, jint freq) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setMetronomeWeakFreq(freq);
}

// ========== НОВЫЕ JNI МЕТОДЫ ДЛЯ МЕТРОНОМА ==========
JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_setMetronomeWaveform(JNIEnv *, jobject,
                                                                           jlong ptr, jint type) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setMetronomeWaveform(type);
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_00024Companion_setMetronomeClickDuration(JNIEnv *, jobject,
                                                                                jlong ptr,
                                                                                jint ms) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setMetronomeClickDuration(ms);
}

JNIEXPORT jint JNICALL
Java_com_example_stagemon_MainActivity_getFohSampleRate(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->getFohSampleRate();
}
JNIEXPORT jint JNICALL
Java_com_example_stagemon_MainActivity_getFohBitDepth(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->getFohBitDepth();
}
JNIEXPORT jint JNICALL
Java_com_example_stagemon_MainActivity_getMonSampleRate(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->getMonSampleRate();
}
JNIEXPORT jint JNICALL
Java_com_example_stagemon_MainActivity_getMonBitDepth(JNIEnv *, jobject, jlong ptr) {
    return reinterpret_cast<OboeEngine *>(ptr)->getMonBitDepth();
}

JNIEXPORT void JNICALL Java_com_example_stagemon_MainActivity_setTrackDataInfo(
        JNIEnv *, jobject, jlong ptr,
        jlong fohDataOffset, jint fohBlockAlign,
        jlong monDataOffset, jint monBlockAlign) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine)
        engine->setTrackDataInfo(fohDataOffset, fohBlockAlign, monDataOffset, monBlockAlign);
}

JNIEXPORT void JNICALL Java_com_example_stagemon_MainActivity_setTrackFdsSeek(
        JNIEnv *, jobject, jlong ptr, jint fohFd, jlong fohDataLength, jint monFd,
        jlong monDataLength) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setTrackFdsSeek(fohFd, fohDataLength, monFd, monDataLength);
}


JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_seekFd(JNIEnv *, jobject, jint fd, jlong position) {
    off_t result = lseek(fd, (off_t) position, SEEK_SET);
    off_t current = lseek(fd, 0, SEEK_CUR);
    LOGD("seekFd fd=%d request=%lld result=%lld current=%lld", fd, (long long) position,
         (long long) result, (long long) current);
}

JNIEXPORT void JNICALL Java_com_example_stagemon_MainActivity_setTrackParams(
        JNIEnv *, jobject, jlong ptr, jint fohSr, jint fohBps, jint fohFmt, jint monSr, jint monBps,
        jint monFmt) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) {
        engine->mFohSampleRate = fohSr;
        engine->mFohBitsPerSample = fohBps;
        engine->mFohFormatTag = fohFmt;
        engine->mMonSampleRate = monSr;
        engine->mMonBitsPerSample = monBps;
        engine->mMonFormatTag = monFmt;
        engine->mFohRawDirect = true;
        engine->mMonRawDirect = true;
        LOGD("setTrackParams: FOH sr=%d bps=%d fmt=%d, MON sr=%d bps=%d fmt=%d",
             fohSr, fohBps, fohFmt, monSr, monBps, monFmt);
    }
}

JNIEXPORT void JNICALL
Java_com_example_stagemon_MainActivity_setPlaybackSpeed(JNIEnv *, jobject, jlong ptr,
                                                        jfloat speed) {
    auto *engine = reinterpret_cast<OboeEngine *>(ptr);
    if (engine) engine->setPlaybackSpeed(speed);
}

}