package top.cywin.onetv.core.data.repositories.user

import android.content.Context
import java.io.BufferedReader

class SensitiveWordFilter(context: Context) {
    private val trie = Trie()

    init {
        // 从assets加载敏感词库
        context.assets.open("sensitive_words.txt").use { inputStream ->
            BufferedReader(inputStream.reader()).useLines { lines ->
                lines.forEach { word ->
                    trie.insert(word.trim())
                }
            }
        }
    }

    fun containsSensitiveWord(input: String): Boolean {
        return trie.containsAny(input)
    }

    private class Trie {
        private val root = Node()

        fun insert(word: String) {
            var current = root
            word.forEach { char ->
                current = current.children.getOrPut(char) { Node() }
            }
            current.isEnd = true
        }

        fun containsAny(text: String): Boolean {
            for (i in text.indices) {
                var node = root
                for (j in i until text.length) {
                    node = node.children[text[j]] ?: break
                    if (node.isEnd) return true
                }
            }
            return false
        }

        private class Node {
            val children = mutableMapOf<Char, Node>()
            var isEnd = false
        }
    }
}