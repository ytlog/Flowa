package com.flowa.app.presentation

import com.flowa.domain.AppRepository
import com.flowa.domain.AppState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Serializes edits; a barrier reports earlier write failures before dependent actions run. */
internal class StateWriter(
    scope: CoroutineScope,
    private val repository: AppRepository,
    onLoaded: (AppState) -> Unit,
    onLoadFailure: (Exception) -> Unit,
    private val onWriteFailure: (Exception) -> Unit,
) {
    private sealed interface Command {
        data class Edit(
            val transform: (AppState) -> AppState,
            val reply: CompletableDeferred<AppState>? = null,
        ) : Command
        data class Flush(val reply: CompletableDeferred<AppState>) : Command
    }

    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        scope.launch {
            var loadFailure: Exception? = null
            var pendingWriteFailure: Exception? = null
            try {
                try {
                    repository.load()
                    onLoaded(repository.state.value)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    loadFailure = failure
                    onLoadFailure(failure)
                }
                for (command in commands) {
                    when (command) {
                        is Command.Edit -> try {
                            loadFailure?.let { throw it }
                            if (command.reply != null) pendingWriteFailure?.let {
                                pendingWriteFailure = null
                                throw it
                            }
                            repository.update(command.transform)
                            command.reply?.complete(repository.state.value)
                        } catch (cancelled: CancellationException) {
                            command.reply?.cancel(cancelled)
                            throw cancelled
                        } catch (failure: Exception) {
                            if (command.reply != null) command.reply.completeExceptionally(failure)
                            else {
                                pendingWriteFailure = failure
                                onWriteFailure(failure)
                            }
                        }
                        is Command.Flush -> {
                            val failure = loadFailure ?: pendingWriteFailure
                            pendingWriteFailure = null
                            if (failure == null) command.reply.complete(repository.state.value)
                            else command.reply.completeExceptionally(failure)
                        }
                    }
                }
            } finally {
                commands.close()
                while (true) {
                    when (val command = commands.tryReceive().getOrNull() ?: break) {
                        is Command.Edit -> command.reply?.cancel()
                        is Command.Flush -> command.reply.cancel()
                    }
                }
            }
        }
    }

    fun enqueue(transform: (AppState) -> AppState) {
        check(commands.trySend(Command.Edit(transform)).isSuccess) { "保存队列已关闭。" }
    }

    fun submit(transform: (AppState) -> AppState): Deferred<AppState> {
        val reply = CompletableDeferred<AppState>()
        check(commands.trySend(Command.Edit(transform, reply)).isSuccess) { "保存队列已关闭。" }
        return reply
    }

    fun snapshot(): Deferred<AppState> {
        val reply = CompletableDeferred<AppState>()
        check(commands.trySend(Command.Flush(reply)).isSuccess) { "保存队列已关闭。" }
        return reply
    }

    suspend fun flush() { snapshot().await() }
}
