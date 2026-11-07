package com.github.epsilon.graphics.schedulers.render2d

/** 累积计数便于比较预热后的增量；CPU 时间不包含 GPU 异步执行时间。 */
class Render2DStats {
    var recordings = 0L; internal set
    var geometryBuilds = 0L; internal set
    var planBuilds = 0L; internal set
    var cacheHits = 0L; internal set
    var uploadedBytes = 0L; internal set
    var drawCalls = 0L; internal set
    var drawRanges = 0L; internal set
    var recordingNanos = 0L; internal set
    var geometryNanos = 0L; internal set
    var planningNanos = 0L; internal set
    var submissionNanos = 0L; internal set
    var uploadNanos = 0L; internal set
    var compositionNanos = 0L; internal set
    var compositionVisits = 0L; internal set
    val unusedResidentBytes get() = residentBytes - liveBytes
    var residentBytes = 0L; internal set
    var liveBytes = 0L; internal set
    var peakBytes = 0L; internal set
    var gpuNanos: Long? = null; internal set
}
