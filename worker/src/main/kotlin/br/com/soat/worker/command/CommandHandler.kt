package br.com.soat.worker.command

import br.com.soat.shared.Command
import kotlin.reflect.KClass

interface CommandHandler {
    val commandType: KClass<out Command>
    fun handle(command: Command)
}
