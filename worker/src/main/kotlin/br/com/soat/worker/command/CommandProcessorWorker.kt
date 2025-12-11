package br.com.soat.worker.command

import br.com.soat.command.CommandProcessor
import br.com.soat.shared.repository.CommandRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class CommandProcessorWorker(
    private val commandRepository: CommandRepository,
    private val commandProcessor: CommandProcessor,
    private val dispatcher: CoroutineDispatcher
) {
    private val logger = LoggerFactory.getLogger(CommandProcessorWorker::class.java)

    private var scope: CoroutineScope? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        scope = CoroutineScope(dispatcher).apply {
            launch {
                logger.info("CommandProcessorWorker started")
                while (isActive) {
                    try {
                        val pendingCommands = commandRepository.findPendingCommands(limit = 10)
                        if (pendingCommands.isNotEmpty()) {
                            commandProcessor.processCommands(pendingCommands)
                        }
                    } catch (e: Exception) {
                        logger.error("Error in CommandProcessorWorker loop", e)
                    }

                    delay(1000)
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        scope?.cancel()
    }
}
