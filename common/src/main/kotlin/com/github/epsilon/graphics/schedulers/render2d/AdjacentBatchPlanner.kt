package com.github.epsilon.graphics.schedulers.render2d

/** 只合并相邻且绑定兼容的命令；资源 A/B/A 必须保留三个范围。 */
internal object AdjacentBatchPlanner {
    data class Group(val commands: List<Render2DCommand>)
    fun plan(commands: List<Render2DCommand>): List<Group> {
        val result = ArrayList<Group>()
        var run = ArrayList<Render2DCommand>()
        var previous: Any? = null
        for (command in commands) {
            val key = bindingKey(command)
            if (run.isNotEmpty() && previous != key) { result.add(Group(run)); run = ArrayList() }
            run.add(command)
            previous = key
        }
        if (run.isNotEmpty()) result.add(Group(run))
        return result
    }

    private fun bindingKey(command: Render2DCommand): Any {
        val material = when (command) {
            is Render2DCommand.Texture -> command.texture
            is Render2DCommand.Text -> command.fontLoader
            is Render2DCommand.GradientText -> command.fontLoader
            is Render2DCommand.BlurText -> command.fontLoader to command.blurRadius
            is Render2DCommand.GlitchText -> command.fontLoader to command.effect
            is Render2DCommand.SegmentedShadow -> command.sequence
            else -> null
        }
        val kind = when (command.kind()) {
            Render2DCommandKind.RECT, Render2DCommandKind.ROUND_RECT, Render2DCommandKind.ROUND_RECT_OUTLINE,
            Render2DCommandKind.TRIANGLE -> Render2DCommandKind.ROUND_RECT
            else -> command.kind()
        }
        return Triple(kind, command.scissor, material)
    }
}
