package wmcloud

// Run: java -cp build/libs/wmcloud-all.jar:build/classes/kotlin/test wmcloud.ConcurrencyTestKt
fun main() {
    check(concurrencyFor(256L shl 20) == (8 to 1)) { "256 MB heap" }
    check(concurrencyFor(512L shl 20) == (16 to 2)) { "512 MB heap" }
    check(concurrencyFor(96L shl 20) == (4 to 1)) { "small heap floors" }
    check(concurrencyFor(8L shl 30) == (16 to 2)) { "big heap caps" }
    // LZMA windows (8 MB per IO thread) must stay within a quarter of the heap.
    for (mb in listOf(128L, 256L, 512L, 1024L)) check(concurrencyFor(mb shl 20).first * 8 <= mb / 4 || mb < 128) { "$mb MB budget" }
    println("concurrency ok")
}
