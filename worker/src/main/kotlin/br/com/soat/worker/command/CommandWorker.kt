package br.com.soat.worker.command

import br.com.soat.shared.CommandStatus
import br.com.soat.shared.repository.CommandRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

class CommandWorker(
    private val commandRepository: CommandRepository,
    private val handlers: List<CommandHandler>,
    private val dispatcher: CoroutineDispatcher
) {
    private val logger = LoggerFactory.getLogger(CommandWorker::class.java)

    private var scope: CoroutineScope? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        scope = CoroutineScope(dispatcher).apply {
            launch {
                logger.info("CommandWorker started")
                while (isActive) {
                    try {
                        processPendingCommands()
                    } catch (e: Exception) {
                        logger.error("Error in CommandWorker loop", e)
                    }
                    delay(1000)
                }
            }
        }
    }

    private fun processPendingCommands() {
        val pending = commandRepository.findPendingCommands(limit = 10)
        logger.info("Found ${pending.size} pending commands")

        for (cmd in pending) {
            try {
                val handler = handlers.find { it.commandType == cmd::class }

                if (handler != null) {
                    handler.handle(cmd)
                    commandRepository.updateStatus(cmd.id, CommandStatus.PROCESSED)
                } else {
                    logger.warn("No handler found for ${cmd::class.simpleName}")
                    commandRepository.updateStatus(cmd.id, CommandStatus.FAILED)
                }
            } catch (e: Exception) {
                logger.error("Failed to process command ${cmd.id}", e)
                commandRepository.updateStatus(cmd.id, CommandStatus.FAILED)
            }
        }
    }

    fun stop() {
        isRunning = false
        scope?.cancel()
    }
}
