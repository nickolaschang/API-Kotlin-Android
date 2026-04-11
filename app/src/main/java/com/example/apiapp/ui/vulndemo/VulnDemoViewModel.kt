package com.example.apiapp.ui.vulndemo

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apiapp.data.repository.AppRepository
import com.example.apiapp.data.repository.StudentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * ViewModel backing [com.example.apiapp.ui.vulndemo.VulnDemoActivity].
 *
 * Runs two independent OWASP API Top 10 demos against the NIT3213
 * dummy API:
 *
 *  1. **Unauthenticated dashboards (API1:2023 — BOLA)** — tapping a
 *     topic chip calls `GET /dashboard/{topic}` with no auth token
 *     and returns the full entity list. Drives the [topicState] LiveData.
 *
 *  2. **Student ID enumeration (API2:2023 — Broken Authentication)** —
 *     fires POST requests at `/sydney/auth` with wrong passwords and
 *     uses the distinct HTTP status codes (400 = exists, 404 = doesn't
 *     exist) to discover valid student IDs. Drives the [scanState]
 *     LiveData.
 *
 * The scan uses a streaming worker pool (see [scanStudents]) to
 * achieve high throughput against the API, since the server has no
 * rate limiting.
 */
@HiltViewModel
class VulnDemoViewModel @Inject constructor(
    private val repository: AppRepository
) : ViewModel() {

    /** State machine for the "unauthenticated topic fetch" section. */
    sealed class TopicState {
        object Idle : TopicState()
        data class Loading(val topic: String) : TopicState()
        data class Success(
            val topic: String,
            val entities: List<Map<String, String>>,
            val total: Int
        ) : TopicState()
        data class Error(val message: String) : TopicState()
    }

    /** State machine for the enumeration scan. */
    sealed class ScanState {
        object Idle : ScanState()
        /** Live scan in progress. Published by the UI publisher coroutine every [UI_UPDATE_MS]. */
        data class Scanning(val scanned: Int, val found: List<String>) : ScanState()
        /** Final snapshot after the user taps Stop (or all workers finish). */
        data class Done(val found: List<String>, val totalScanned: Int) : ScanState()
        data class Error(val message: String) : ScanState()
    }

    private val _topicState = MutableLiveData<TopicState>(TopicState.Idle)
    val topicState: LiveData<TopicState> = _topicState

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    // Scan control state. `scanJob` is kept so we can detect "already
    // running" in [scanStudents]. `stopRequested` is a @Volatile flag
    // that workers check on every loop iteration so they can all exit
    // at the next HTTP round-trip when the user taps Stop.
    private var scanJob: Job? = null
    @Volatile private var stopRequested = false

    /**
     * Fetches a single topic's dashboard entities WITHOUT any auth —
     * demonstrating OWASP API1:2023 Broken Object Level Authorization.
     *
     * The keypass is supposed to be earned through `/sydney/auth`, but
     * the dummy API accepts any valid topic word directly, so we pass
     * the topic chip's text straight through as the path segment.
     */
    fun fetchTopic(topic: String) {
        viewModelScope.launch {
            _topicState.value = TopicState.Loading(topic)
            val result = repository.getDashboard(topic)
            _topicState.value = result.fold(
                onSuccess = { response ->
                    TopicState.Success(topic, response.entities, response.entityTotal)
                },
                onFailure = { e ->
                    TopicState.Error(e.message ?: "Failed to fetch $topic")
                }
            )
        }
    }

    /**
     * Kicks off the student-ID enumeration scan from [startId] and
     * runs until the user taps Stop.
     *
     * **Design: streaming worker pool.** Instead of the naive
     * "batch, await, next batch" approach (which wastes time waiting
     * for the slowest request in every batch), we launch
     * [WORKER_COUNT] independent coroutines that each run a tight
     * loop: grab the next ID from a shared [AtomicInteger] counter,
     * probe it, record the result, repeat. This means there's never a
     * barrier — if one request is slow, the other 127 workers keep
     * going.
     *
     * **Thread safety.** Workers share three pieces of state:
     *  - `offset` — next ID to scan. `getAndIncrement()` is atomic so
     *    two workers never get the same ID.
     *  - `scanned` — count of completed probes. Atomic counter.
     *  - `found` — list of confirmed student IDs.
     *    [CopyOnWriteArrayList] gives us lock-free reads plus safe
     *    concurrent writes.
     *
     * **UI updates.** Publishing `_scanState` once per HTTP response
     * would flood LiveData with hundreds of updates per second.
     * Instead a dedicated [uiPublisher] coroutine wakes up every
     * [UI_UPDATE_MS] ms and posts the current counter + found list.
     *
     * **Stopping.** [stopScan] flips the volatile `stopRequested`
     * flag. Workers check it on every iteration and return; the
     * uiPublisher is cancelled explicitly once all workers have
     * joined. A final [ScanState.Done] is then emitted with the
     * grand total.
     */
    fun scanStudents(startId: Int) {
        // Ignore if a scan is already running — protects against
        // double taps on the Start button during the short window
        // before the state machine transitions to Scanning.
        if (scanJob?.isActive == true) return

        stopRequested = false
        scanJob = viewModelScope.launch {
            // Immediate feedback so the progress bar shows up right away,
            // before any HTTP request has even been made.
            _scanState.value = ScanState.Scanning(scanned = 0, found = emptyList())

            val offset = AtomicInteger(0)
            val scanned = AtomicInteger(0)
            val found = CopyOnWriteArrayList<String>()

            val workers = List(WORKER_COUNT) {
                launch(Dispatchers.IO) {
                    while (!stopRequested && isActive) {
                        val i = offset.getAndIncrement()
                        val id = "s${startId + i}"
                        val status = repository.enumerateStudent(id)
                        // Second stop check — in case Stop was pressed
                        // while this request was in flight, skip the
                        // accounting work and bail out.
                        if (stopRequested) return@launch
                        if (status == StudentStatus.EXISTS || status == StudentStatus.LOGGED_IN) {
                            found.add(id)
                        }
                        scanned.incrementAndGet()
                    }
                }
            }

            // Dedicated coroutine that throttles LiveData emissions.
            val uiPublisher = launch {
                while (!stopRequested && isActive) {
                    delay(UI_UPDATE_MS)
                    _scanState.value = ScanState.Scanning(
                        scanned = scanned.get(),
                        found = found.toList()
                    )
                }
            }

            workers.joinAll()
            uiPublisher.cancel()

            _scanState.value = ScanState.Done(
                found = found.toList(),
                totalScanned = scanned.get()
            )
        }
    }

    /** Signals all scan workers to exit on their next loop iteration. */
    fun stopScan() {
        stopRequested = true
    }

    /** Cancels any running scan and resets the state back to Idle. */
    fun resetScan() {
        stopRequested = true
        _scanState.value = ScanState.Idle
    }

    /**
     * List of valid topic keywords discovered against the NIT3213 API.
     * Each of these can be passed to `/dashboard/{topic}` to get a
     * different entity list — and none of them require authentication,
     * which is the whole point of the demo.
     */
    val topics = listOf(
        "food", "music", "movies", "books", "travel",
        "animals", "science", "history", "art", "technology",
        "sports", "plants", "languages", "fashion", "architecture",
        "mythology", "fitness", "photography"
    )

    companion object {
        // Number of coroutines hammering the API in parallel. Must not exceed
        // the OkHttp Dispatcher's maxRequestsPerHost (see AppModule).
        private const val WORKER_COUNT = 128

        // How often the UI is refreshed while a scan is in progress (ms).
        private const val UI_UPDATE_MS = 150L
    }
}
