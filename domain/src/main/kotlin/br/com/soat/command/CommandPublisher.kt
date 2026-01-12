package br.com.soat.command

import br.com.soat.command.model.Command

interface CommandPublisher {
    fun publish(command: Command)
}