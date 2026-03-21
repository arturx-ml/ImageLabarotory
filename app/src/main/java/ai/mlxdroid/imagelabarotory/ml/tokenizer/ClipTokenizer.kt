package ai.mlxdroid.imagelabarotory.ml.tokenizer

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * CLIP tokenizer implementing Byte Pair Encoding (BPE) for text-to-token conversion.
 *
 * This tokenizer converts text prompts into arrays of token IDs that can be processed
 * by the text encoder model. It implements the same tokenization scheme used by
 * OpenAI's CLIP model.
 *
 * @param vocabFile File containing the vocabulary mapping (token -> ID)
 * @param mergesFile File containing BPE merge rules
 */
class ClipTokenizer(
    private val vocabFile: File,
    private val mergesFile: File
) {
    private val vocab: Map<String, Int>
    private val bpeMerges: Map<Pair<String, String>, Int>
    private val cache = mutableMapOf<String, List<String>>()

    companion object {
        private const val TAG = "ClipTokenizer"
        const val SOT_TOKEN = "<|startoftext|>"  // Start of text token
        const val EOT_TOKEN = "<|endoftext|>"    // End of text token
        const val PAD_TOKEN = "<|endoftext|>"    // Padding uses EOT
        const val MAX_LENGTH = 77                 // CLIP's max sequence length

        /**
         * Create tokenizer from Android assets
         */
        fun fromAssets(context: Context): ClipTokenizer {
            val vocabFile = File(context.cacheDir, "vocab.json")
            val mergesFile = File(context.cacheDir, "merges.txt")

            // Copy from assets to cache if not already present
            if (!vocabFile.exists()) {
                context.assets.open("tokenizer/vocab.json").use { input ->
                    vocabFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            if (!mergesFile.exists()) {
                context.assets.open("tokenizer/merges.txt").use { input ->
                    mergesFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            return ClipTokenizer(vocabFile, mergesFile)
        }
    }

    init {
        Log.d(TAG, "Loading vocabulary from ${vocabFile.absolutePath}")
        vocab = loadVocab(vocabFile)
        Log.d(TAG, "Loaded ${vocab.size} vocabulary entries")

        Log.d(TAG, "Loading BPE merges from ${mergesFile.absolutePath}")
        bpeMerges = loadMerges(mergesFile)
        Log.d(TAG, "Loaded ${bpeMerges.size} BPE merge rules")
    }

    /**
     * Encode text to token IDs
     *
     * @param text Input text to tokenize
     * @param maxLength Maximum sequence length (default 77)
     * @return IntArray of token IDs, padded to maxLength
     */
    fun encode(text: String, maxLength: Int = MAX_LENGTH): IntArray {
        // Normalize text: lowercase and clean whitespace
        val normalizedText = text.lowercase().trim()

        if (normalizedText.isEmpty()) {
            // Return just SOT and EOT tokens followed by padding
            return IntArray(maxLength) { i ->
                when (i) {
                    0 -> vocab[SOT_TOKEN] ?: 49406
                    1 -> vocab[EOT_TOKEN] ?: 49407
                    else -> vocab[PAD_TOKEN] ?: 49407
                }
            }
        }

        // Tokenize text using BPE
        val tokens = mutableListOf<String>()
        tokens.add(SOT_TOKEN)

        // Split by whitespace and tokenize each word
        val words = normalizedText.split(Regex("\\s+"))
        for (word in words) {
            if (word.isNotEmpty()) {
                val wordTokens = bpe(word)
                tokens.addAll(wordTokens)
            }
        }

        tokens.add(EOT_TOKEN)

        // Convert tokens to IDs
        val tokenIds = tokens.mapNotNull { token ->
            vocab[token].also { id ->
                if (id == null) {
                    Log.w(TAG, "Unknown token: $token")
                }
            }
        }

        // Truncate or pad to maxLength
        return IntArray(maxLength) { i ->
            if (i < tokenIds.size) {
                tokenIds[i]
            } else {
                vocab[PAD_TOKEN] ?: 49407
            }
        }
    }

    /**
     * Apply Byte Pair Encoding to a word
     */
    private fun bpe(word: String): List<String> {
        // Check cache first
        cache[word]?.let { return it }

        // Convert word to list of characters with end-of-word marker
        var wordChars = word.toList().map { it.toString() }.toMutableList()
        if (wordChars.isNotEmpty()) {
            wordChars[wordChars.size - 1] = wordChars.last() + "</w>"
        }

        // Iteratively merge the most common pairs
        while (wordChars.size > 1) {
            // Find all adjacent pairs
            val pairs = wordChars.zipWithNext()

            // Find the pair with the lowest merge rank (most common)
            val pairToMerge = pairs
                .mapNotNull { (first, second) ->
                    bpeMerges[Pair(first, second)]?.let { rank ->
                        Triple(first, second, rank)
                    }
                }
                .minByOrNull { it.third }

            // If no mergeable pairs found, we're done
            if (pairToMerge == null) break

            // Merge the pair
            val (first, second, _) = pairToMerge
            val merged = first + second

            val newWordChars = mutableListOf<String>()
            var i = 0
            while (i < wordChars.size) {
                if (i < wordChars.size - 1 &&
                    wordChars[i] == first &&
                    wordChars[i + 1] == second) {
                    newWordChars.add(merged)
                    i += 2
                } else {
                    newWordChars.add(wordChars[i])
                    i += 1
                }
            }

            wordChars = newWordChars
        }

        // Cache the result
        cache[word] = wordChars

        return wordChars
    }

    /**
     * Load vocabulary from JSON file
     */
    private fun loadVocab(file: File): Map<String, Int> {
        val jsonString = file.readText()
        val jsonObject = JSONObject(jsonString)
        val vocabMap = mutableMapOf<String, Int>()

        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            vocabMap[key] = jsonObject.getInt(key)
        }

        return vocabMap
    }

    /**
     * Load BPE merges from text file
     */
    private fun loadMerges(file: File): Map<Pair<String, String>, Int> {
        val mergesMap = mutableMapOf<Pair<String, String>, Int>()

        file.bufferedReader().use { reader ->
            // Skip header line if present
            var firstLine = true
            reader.forEachLine { line ->
                if (firstLine && line.startsWith("#")) {
                    firstLine = false
                    return@forEachLine
                }
                firstLine = false

                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val pair = Pair(parts[0], parts[1])
                    mergesMap[pair] = mergesMap.size
                }
            }
        }

        return mergesMap
    }

    /**
     * Decode token IDs back to text (for debugging/validation)
     */
    fun decode(tokenIds: IntArray): String {
        val idToToken = vocab.entries.associate { it.value to it.key }

        return tokenIds
            .toList()
            .mapNotNull { id -> idToToken[id] }
            .filter { it != SOT_TOKEN && it != EOT_TOKEN && it != PAD_TOKEN }
            .joinToString("")
            .replace("</w>", " ")
            .trim()
    }
}
