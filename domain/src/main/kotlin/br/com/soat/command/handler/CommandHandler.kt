package br.com.soat.command.handler

import br.com.soat.command.model.Command
import kotlin.reflect.KClass

interface CommandHandler {
    val commandType: KClass<out Command>
    fun handle(command: Command)
}