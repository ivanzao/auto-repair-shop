package br.com.soat.worker.command

import br.com.soat.command.CommandProcessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class CommandProcessorWorker(
    private val commandProcessor: CommandProcessor,
    private val dispatcher: CoroutineDispatcher
) {

    private val logger = LoggerFactory.getLogger(CommandProcessorWorker::class.java)

    private var scope: CoroutineScope? = null
    private var isRunning = false
    private var job: Job? = null

    fun start() {
        if (isRunning) return
        isRunning = true

        scope = CoroutineScope(dispatcher).apply {
            job = launch {
                logger.info("CommandProcessorWorker started")
                while (isRunning) {
                    try {
                        commandProcessor.processCommands()
                        delay(1000)
                    } catch (e: Exception) {
                        logger.error("Error in CommandProcessorWorker loop", e)
                        delay(5000)
                    }
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        runBlocking { job?.join() }
        scope?.cancel()
    }
}
