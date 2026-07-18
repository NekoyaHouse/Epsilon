package com.github.epsilon.assets.i18n;

import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class LanguageReloadListener implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor exectutor, PreparationBarrier barrier, Executor applyExectutor) {
        return CompletableFuture.completedFuture(null)
                .thenCompose(barrier::wait)
                .thenRunAsync(() -> {

                    EpsilonLanguageManager.INSTANCE.reload(sharedState.resourceManager());

                }, applyExectutor);
    }

}
