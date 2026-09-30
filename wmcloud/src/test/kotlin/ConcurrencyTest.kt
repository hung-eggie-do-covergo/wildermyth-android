package wmcloud

// Run: java -cp build/libs/wmcloud-all.jar:build/classes/kotlin/test wmcloud.ConcurrencyTestKt
fun main() {
    check(concurrencyFor(256L shl 20) == (4 to 1)) { "256 MB heap" }
    check(concurrencyFor(512L shl 20) == (8 to 2)) { "512 MB heap" }
    check(concurrencyFor(96L shl 20) == (2 to 1)) { "small heap floors" }
    check(concurrencyFor(8L shl 30) == (8 to 2)) { "big heap caps" }
    println("concurrency ok")
}
