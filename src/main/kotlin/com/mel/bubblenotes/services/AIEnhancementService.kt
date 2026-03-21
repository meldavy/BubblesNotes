package com.mel.bubblenotes.services

import com.mel.bubblenotes.models.Note
import com.mel.bubblenotes.repositories.AITaskRepository
import com.mel.bubblenotes.repositories.AITaskRepository.AITaskResult
import com.mel.bubblenotes.repositories.NoteRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for AI-powered note enhancements.
 * Orchestrates AI task creation, processing, and result storage.
 * Follows testability constitution: dependencies are injectable.
 *
 * Background scheduler runs every 30 seconds to process pending AI tasks.
 */
class AIEnhancementService(
    private val openAIClient: OpenAIClient,
    private val aiTaskRepository: AITaskRepository,
    private val noteRepository: NoteRepository,
    private val schedulerScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    // 30 seconds default
    private val schedulerIntervalMs: Long = 30000,
    private val workerId: String = "worker-${ProcessHandle.current().pid()}-${Thread.currentThread().id}",
) {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AIEnhancementService::class.java)
    }

    private val processingTasks = ConcurrentHashMap<Long, Job>()

    // Track scheduler state for monitoring
    private val _schedulerRunning = MutableStateFlow(false)
    val schedulerRunning: StateFlow<Boolean> = _schedulerRunning.asStateFlow()

    /**
     * Create an AI task for a note.
     * This will be processed asynchronously by the scheduler.
     * @param noteId The note ID to enhance
     * @return The created task ID
     */
    fun createAITask(noteId: Long): Long {
        logger.info("createAITask: Creating AI task for note $noteId")
        val taskId = aiTaskRepository.create(noteId)
        logger.info("createAITask: Created AI task $taskId for note $noteId")

        // Schedule the task for processing
        logger.debug("createAITask: Scheduling task $taskId for async processing")
        scheduleTask(taskId)

        return taskId
    }

    /**
     * Schedule a task for async processing.
     */
    private fun scheduleTask(taskId: Long) {
        // Cancel any existing job for this task
        val existingJob = processingTasks[taskId]
        if (existingJob != null) {
            logger.debug("scheduleTask: Cancelling existing job for task $taskId")
            existingJob.cancel()
        }

        logger.debug("scheduleTask: Launching new job for task $taskId")
        val job =
            schedulerScope.launch {
                processTask(taskId)
            }
        processingTasks[taskId] = job
        logger.debug("scheduleTask: Job scheduled for task $taskId, total active jobs: ${processingTasks.size}")
    }

    /**
     * Process a single AI task.
     */
    private suspend fun processTask(taskId: Long) {
        logger.info("processTask: Starting processing for task $taskId")

        try {
            // Get the task
            logger.debug("processTask: Fetching task $taskId from repository")
            val task = aiTaskRepository.findById(taskId)
            if (task == null) {
                logger.error("processTask: Task $taskId not found in repository")
                return
            }
            logger.debug("processTask: Found task $taskId with status ${task.status} for note ${task.noteId}")

            // Mark as processing with worker ID for distributed locking
            logger.debug("processTask: Marking task $taskId as processing with workerId=$workerId")
            if (!aiTaskRepository.setProcessing(taskId, workerId = workerId, lockTimeout = 300000)) {
                logger.warn("processTask: Failed to mark task $taskId as processing (may have been picked up by another worker)")
                return
            }
            logger.info("processTask: Task $taskId claimed by worker $workerId")

            // Get the note
            logger.debug("processTask: Fetching note ${task.noteId} from repository")
            val note = noteRepository.findById(task.noteId)
            if (note == null) {
                val errorMsg = "processTask: Note ${task.noteId} not found"
                logger.error(errorMsg)
                aiTaskRepository.setFailed(taskId, errorMsg)
                return
            }
            logger.info(
                "processTask: Found note ${note.id} with content length: ${note.content.length} chars, tags: ${note.tags?.size ?: 0}",
            )

            // Get all unique tags from all notes for context
            val existingTags = noteRepository.getUniqueTagsByUserId(note.userId)
            logger.debug("processTask: All unique tags for user ${note.userId}: ${existingTags.size} tags")

            // Call OpenAI for enhancements
            logger.info("processTask: Calling OpenAI for enhancements on note ${note.id} (task $taskId)")
            val startTime = System.currentTimeMillis()
            val enhancements =
                openAIClient.generateAllAIEnhancements(
                    content = note.content,
                    existingTags = existingTags,
                )
            val elapsed = System.currentTimeMillis() - startTime
            logger.info("processTask: OpenAI call completed in ${elapsed}ms for note ${note.id}")

            // Prepare result (OpenAIClient.AITaskResult has aiTitle, aiSummary, aiTags)
            val result =
                AITaskResult(
                    aiTitle = enhancements.aiTitle,
                    aiSummary = enhancements.aiSummary,
                    aiTags = enhancements.aiTags,
                )
            logger.debug(
                "processTask: Prepared result - title: ${result.aiTitle}, summary: ${result.aiSummary != null}, tags: ${result.aiTags.size}",
            )

            // Update task as completed
            logger.debug("processTask: Marking task $taskId as completed")
            if (aiTaskRepository.setCompleted(taskId, result)) {
                logger.info(
                    "processTask: Completed AI task $taskId for note ${note.id} - title: ${result.aiTitle?.take(
                        50,
                    )}, summary: ${result.aiSummary != null}, tags: ${result.aiTags.size}",
                )
            } else {
                logger.error("processTask: Failed to update task $taskId as completed")
            }

            // Update note with AI enhancements
            logger.debug("processTask: Updating note ${note.id} with AI enhancements")
            if (noteRepository.updateAIEnhancements(
                    noteId = task.noteId,
                    aiTitle = result.aiTitle,
                    aiSummary = result.aiSummary,
                    aiTags = result.aiTags,
                )
            ) {
                logger.info("processTask: Updated note ${note.id} with AI enhancements")
            } else {
                logger.error("processTask: Failed to update note ${note.id} with AI enhancements")
            }
        } catch (e: Exception) {
            logger.error("processTask: Error processing AI task $taskId: ${e.message}", e)
            logger.debug("processTask: Exception stack trace:", e)
            // Reset task to PENDING so it can be retried in the next iteration
            // This releases the lock and allows other workers to pick it up
            aiTaskRepository.resetToPending(taskId)
            logger.info("processTask: Reset task $taskId to PENDING for retry")
        }
    }

    /**
     * Start the background scheduler that periodically processes pending AI tasks.
     */
    fun start() {
        if (_schedulerRunning.value) {
            logger.warn("Scheduler already running")
            return
        }

        logger.info("Starting AI Enhancement Service scheduler (interval: ${schedulerIntervalMs}ms)")
        _schedulerRunning.value = true

        schedulerScope.launch {
            while (isActive) {
                try {
                    processPendingTasks()
                } catch (e: Exception) {
                    logger.error("Error in scheduler loop: ${e.message}", e)
                }
                delay(schedulerIntervalMs)
            }
        }
    }

    /**
     * Process all pending tasks from the database.
     */
    private suspend fun processPendingTasks() {
        logger.info("processPendingTasks: Fetching pending tasks (limit: 10)")
        val pendingTasks = aiTaskRepository.findPendingTasks(10)
        if (pendingTasks.isEmpty()) {
            logger.info("processPendingTasks: No pending AI tasks to process (scheduler running)")
            return
        }

        logger.info("processPendingTasks: Found ${pendingTasks.size} pending AI tasks to process")
        logger.debug("processPendingTasks: Task IDs: ${pendingTasks.map { it.id }}")

        pendingTasks.forEach { task ->
            val isActive = processingTasks[task.id]?.isActive
            logger.debug("processPendingTasks: Task ${task.id} isActive=$isActive")
            if (isActive != true) {
                logger.info("processPendingTasks: Scheduling task ${task.id} for processing")
                scheduleTask(task.id)
            } else {
                logger.debug("processPendingTasks: Task ${task.id} already being processed, skipping")
            }
        }
        logger.debug("processPendingTasks: Finished processing pending tasks, total active jobs: ${processingTasks.size}")
    }

    /**
     * Stop the scheduler and cancel all processing tasks.
     */
    fun stop() {
        logger.info("stop: Stopping AI Enhancement Service scheduler...")
        _schedulerRunning.value = false

        // Cancel all processing jobs
        val activeJobs = processingTasks.size
        logger.info("stop: Cancelling $activeJobs active processing jobs")
        processingTasks.values.forEach { it.cancel() }
        processingTasks.clear()

        // Cancel the scheduler scope
        logger.debug("stop: Cancelling scheduler scope")
        schedulerScope.cancel()

        logger.info("stop: AI Enhancement Service stopped")
    }

    /**
     * Get the status of an AI task.
     * @param taskId The task ID
     * @return The task status or null if not found
     */
    fun getTaskStatus(taskId: Long): AITaskRepository.Status? {
        logger.debug("getTaskStatus: Fetching status for task $taskId")
        val status = aiTaskRepository.findById(taskId)?.status
        logger.debug("getTaskStatus: Task $taskId status = $status")
        return status
    }

    /**
     * Get AI task results for a note.
     * @param noteId The note ID
     * @return The latest completed task result or null
     */
    fun getAITaskResultsForNote(noteId: Long): AITaskResult? {
        logger.debug("getAITaskResultsForNote: Fetching results for note $noteId")
        // Find the latest completed task for this note
        val pendingTasks = aiTaskRepository.findPendingTasks(100)
        val allTasks = pendingTasks.filter { it.noteId == noteId }
        logger.debug("getAITaskResultsForNote: Found ${allTasks.size} tasks for note $noteId")

        val completedTasks = allTasks.filter { it.status == AITaskRepository.Status.COMPLETED }
        logger.debug("getAITaskResultsForNote: Found ${completedTasks.size} completed tasks for note $noteId")

        val result =
            completedTasks
                .maxByOrNull { it.completedAt ?: 0 }
                ?.result

        if (result != null) {
            logger.info(
                "getAITaskResultsForNote: Found result for note $noteId - title: ${result.aiTitle}, summary: ${result.aiSummary != null}, tags: ${result.aiTags.size}",
            )
        } else {
            logger.debug("getAITaskResultsForNote: No result found for note $noteId")
        }
        return result
    }

    /**
     * Process a note immediately (synchronous, for testing or immediate enhancement).
     * @param note The note to enhance
     * @return The AI enhancements
     */
    suspend fun enhanceNoteImmediately(note: Note): OpenAIClient.AITaskResult {
        logger.info("enhanceNoteImmediately: Enhancing note ${note.id} synchronously")
        // Get all unique tags from all notes for context
        val existingTags = noteRepository.getUniqueTagsByUserId(note.userId)
        logger.debug(
            "enhanceNoteImmediately: Note ${note.id} content length: ${note.content.length} chars, all unique tags: ${existingTags.size}",
        )
        val startTime = System.currentTimeMillis()
        val result =
            openAIClient.generateAllAIEnhancements(
                content = note.content,
                existingTags = existingTags,
            )
        val elapsed = System.currentTimeMillis() - startTime
        logger.info(
            "enhanceNoteImmediately: Completed enhancing note ${note.id} in ${elapsed}ms - title: ${result.aiTitle}, summary: ${result.aiSummary != null}, tags: ${result.aiTags.size}",
        )
        return result
    }
}
