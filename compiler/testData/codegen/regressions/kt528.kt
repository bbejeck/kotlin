namespace mask

import std.io.*
import java.io.*
import java.util.*
import java.util.Iterator as It
import java.lang.Iterable as Itl

fun box() : String {
    val input = StringReader("/Users/abreslav/work/jet/docs/luhnybin/src/test")

    val luhny = Luhny()
    input.forEachChar {
        luhny.charIn(it)
    }
    luhny.printAll()
    return "OK"
}

class Luhny() {
    val buffer = LinkedList<Char>()
    val digits = LinkedList<Int>()

    var toBeMasked = 0

    fun charIn(it : Char) {
        buffer.addLast(it)
        when (it) {
            .isDigit() => digits.addLast(it.int - '0'.int)
            ' ', '-' => {}
            else => {
                    printAll()
                    digits.clear()
            }
        }
        if (digits.size() > 16)
          printOneDigit()
        check()
    }

    fun check() {
        if (digits.size() < 14) return
        print("check")
        val sum = digits.sum { i, d =>
//            println("$i -> $d")
            if (i % 2 == digits.size()) {
                val f = d * 2 / 10
                val s = d * 2 % 10
//                println("$d: f = $f, s = $s")
                (f + s).pr {
//                    println("to be doubled: $i -> $d : $it")
                }
            } else d
        }
//        println(sum)
        if (sum % 10 == 0) {print("s"); toBeMasked = digits.size()}
    }

    fun printOneDigit() {
        while (!buffer.isEmpty()) {
            val c = buffer.removeFirst()
            out(c)
            if (c.isDigit()) {
                digits.removeFirst()
                return
            }
        }
    }

    fun printAll() {
        while (!buffer.isEmpty())
          out(buffer.removeFirst())
    }

    fun out(c : Char) {
        if (toBeMasked > 0) {
            print('X')
            toBeMasked--
        }
        else {
            print(c)
        }
    }
}

fun <T> T.pr(f : fun(T) : Unit) : T {
    f(this)
    return this
}

fun LinkedList<Int>.sum(f : fun(Int, Int ): Int): Int {
    var sum = 0
    for (i in 1..size()) {
        val j = size() - i
        sum += f(j, get(j))
    }
    return sum
}

//fun <T> List<T>.backwards() : Itl<T> = object : Itl<T> {
//  override fun iterator() : It<T> =
//      object : It<T> {
//          var current = size()
//          override fun next() : T = get(--current)
//          override fun hasNext() : Boolean = current > 0
//          override fun remove() {throw UnsupportedOperationException()}
//      }
//}

fun Char.isDigit() = Character.isDigit(this)

fun Reader.forEachChar(body : fun(Char) : Unit) {
    do {
        var i = read();
        if (i == -1) break
        body(i.chr)
    } while(true)
}