package com.github.epsilon.utils.client

import com.github.epsilon.Constants

object ClientUtils {

    /**
     * 判断客户端是否仍处于资源加载或重载阶段。
     *
     * @return 仍在加载时返回 true
     */
    fun isLoading(): Boolean {
        val state = Constants.mc.reloadStateTracker.reloadState
        return state == null || !state.finished
    }

}
