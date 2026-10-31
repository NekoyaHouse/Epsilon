package com.github.epsilon.assets.i18n

import net.minecraft.server.packs.resources.PreparableReloadListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

class LanguageReloadListener : PreparableReloadListener {

    override fun reload(
        sharedState: PreparableReloadListener.SharedState,
        executor: Executor,
        barrier: PreparableReloadListener.PreparationBarrier,
        applyExecutor: Executor,
    ): CompletableFuture<Void> {
        return CompletableFuture.completedFuture<Void>(null)
            .thenCompose(barrier::wait)
            .thenRunAsync({
                EpsilonLanguageManager.reload(sharedState.resourceManager())
            }, applyExecutor)
    }

}
