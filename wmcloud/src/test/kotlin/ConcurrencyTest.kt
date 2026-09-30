package wmcloud

// Run: java -cp build/classes/kotlin/main:build/classes/kotlin/test:build/libs/wmcloud-all.jar wmcloud.ConcurrencyTestKt
fun main() {
    check(concurrencyFor(256L shl 20, 8) == (16 to 8)) { "256 MB heap" }
    check(concurrencyFor(512L shl 20, 8) == (32 to 8)) { "512 MB heap" }
    check(concurrencyFor(64L shl 20, 8) == (8 to 2)) { "small heap floors" }
    check(concurrencyFor(8L shl 30, 64) == (32 to 16)) { "big heap caps" }
    // LZMA windows (8 MB per IO thread) must stay within a quarter of the heap.
    for (mb in listOf(128L, 256L, 512L, 1024L)) check(ioThreadsFor(mb shl 20) * 8 <= mb / 4) { "$mb MB budget" }
    println("concurrency ok")
}
