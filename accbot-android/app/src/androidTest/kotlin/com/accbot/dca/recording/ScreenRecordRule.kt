package com.accbot.dca.recording

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * JUnit rule that records the device screen during a test using `screenrecord`.
 *
 * The recording is saved to [outputDir]/[test method name].mp4 on the device.
 * Pull it afterwards with: adb pull /sdcard/Movies/<name>.mp4
 */
class ScreenRecordRule(
    private val outputDir: String = "/sdcard/Movies",
    private val resolution: String = "1280x720",
    private val bitRate: Int = 6_000_000,
    private val timeLimitSec: Int = 180
) : TestWatcher() {

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private var recordingThread: Thread? = null
    private val recordingStarted = CountDownLatch(1)

    @Volatile
    private var outputPath: String = ""

    override fun starting(description: Description) {
        val name = description.methodName
            ?.replace(Regex("[^a-zA-Z0-9_]"), "_")
            ?: "screen_recording"

        outputPath = "$outputDir/$name.mp4"

        // Show visual touch indicators in the recording
        device.executeShellCommand("settings put system show_touches 1")

        // screenrecord blocks until recording finishes, so run in a background thread
        recordingThread = Thread({
            recordingStarted.countDown()
            device.executeShellCommand(
                "screenrecord --size $resolution --bit-rate $bitRate " +
                    "--time-limit $timeLimitSec $outputPath"
            )
        }, "screen-record-$name").apply {
            isDaemon = true
            start()
        }

        // Wait for the thread to actually start (ensures screenrecord process is launched)
        recordingStarted.await(5, TimeUnit.SECONDS)
        // Small delay to let screenrecord initialize
        Thread.sleep(1_000)
    }

    override fun finished(description: Description) {
        // Hide visual touch indicators
        device.executeShellCommand("settings put system show_touches 0")

        // Send SIGINT to screenrecord for graceful stop (writes proper MP4 trailer)
        device.executeShellCommand("pkill -2 screenrecord")

        // Wait for the recording thread to finish (file finalization)
        recordingThread?.join(10_000)
        recordingThread = null

        // Small delay to ensure the file is fully written
        Thread.sleep(2_000)
    }
}
