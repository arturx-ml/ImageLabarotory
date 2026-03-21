package ai.mlxdroid.imagelabarotory.ml.tokenizer

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/**
 * Unit tests for ClipTokenizer
 *
 * These tests verify that the tokenizer correctly implements CLIP's
 * Byte Pair Encoding algorithm and produces expected token sequences.
 */
class ClipTokenizerTest {

    private lateinit var tokenizer: ClipTokenizer

    @Before
    fun setup() {
        // For unit tests, we need to provide paths to vocab and merges files
        // In actual app, these come from assets via fromAssets()
        val vocabFile = File("src/main/assets/tokenizer/vocab.json")
        val mergesFile = File("src/main/assets/tokenizer/merges.txt")

        // Skip tests if files don't exist
        if (!vocabFile.exists() || !mergesFile.exists()) {
            println("Skipping tokenizer tests - asset files not found")
            println("Run from project root directory")
            return
        }

        tokenizer = ClipTokenizer(vocabFile, mergesFile)
    }

    @Test
    fun `tokenize simple prompt`() {
        if (!this::tokenizer.isInitialized) return

        val tokens = tokenizer.encode("a photo of a cat")

        // Should start with SOT token (49406)
        assertEquals(49406, tokens[0])

        // Should be padded to 77 tokens
        assertEquals(77, tokens.size)

        // Should end with EOT followed by padding (49407)
        assertTrue(tokens.takeLast(10).all { it == 49407 })

        // Print for manual inspection
        println("Tokens for 'a photo of a cat': ${tokens.take(10).joinToString()}")
    }

    @Test
    fun `tokenize empty string`() {
        if (!this::tokenizer.isInitialized) return

        val tokens = tokenizer.encode("")

        // Should have SOT at position 0
        assertEquals(49406, tokens[0])

        // Should have EOT at position 1
        assertEquals(49407, tokens[1])

        // Rest should be padding
        assertTrue(tokens.drop(2).all { it == 49407 })
    }

    @Test
    fun `tokenize long prompt with truncation`() {
        if (!this::tokenizer.isInitialized) return

        // Create a very long prompt
        val longPrompt = "a beautiful " + "stunning magnificent amazing incredible ".repeat(20)

        val tokens = tokenizer.encode(longPrompt)

        // Should still be exactly 77 tokens
        assertEquals(77, tokens.size)

        // Should start with SOT
        assertEquals(49406, tokens[0])

        println("Long prompt tokenized to ${tokens.size} tokens")
    }

    @Test
    fun `tokenize typical Stable Diffusion prompt`() {
        if (!this::tokenizer.isInitialized) return

        val prompt = "a beautiful landscape with mountains and a lake, sunset, highly detailed"

        val tokens = tokenizer.encode(prompt)

        assertEquals(77, tokens.size)
        assertEquals(49406, tokens[0])

        // Count non-padding tokens
        val nonPaddingCount = tokens.count { it != 49407 || it == tokens[1] }
        println("Prompt '$prompt' has $nonPaddingCount tokens (including SOT/EOT)")

        // Should have at least SOT + a few words + EOT
        assertTrue(nonPaddingCount >= 3)
    }

    @Test
    fun `decode tokens back to text`() {
        if (!this::tokenizer.isInitialized) return

        val originalText = "a red apple"
        val tokens = tokenizer.encode(originalText)
        val decoded = tokenizer.decode(tokens)

        println("Original: '$originalText'")
        println("Decoded: '$decoded'")

        // Should be similar (may have slight differences due to BPE)
        assertTrue(decoded.contains("red"))
        assertTrue(decoded.contains("apple"))
    }

    @Test
    fun `tokenize multiple prompts consistently`() {
        if (!this::tokenizer.isInitialized) return

        val prompt = "a cat"

        // Tokenize the same prompt twice
        val tokens1 = tokenizer.encode(prompt)
        val tokens2 = tokenizer.encode(prompt)

        // Should produce identical results
        assertArrayEquals(tokens1, tokens2)
    }

    @Test
    fun `verify token array structure`() {
        if (!this::tokenizer.isInitialized) return

        val tokens = tokenizer.encode("test prompt")

        // All tokens should be non-negative
        assertTrue(tokens.all { it >= 0 })

        // First token should be SOT (49406)
        assertEquals(49406, tokens[0])

        // Find where padding starts (continuous 49407s)
        val firstPadIndex = tokens.indexOfFirst { it == 49407 }
        if (firstPadIndex > 0) {
            // Everything after first EOT should be padding
            assertTrue(tokens.drop(firstPadIndex + 1).all { it == 49407 })
        }
    }

    @Test
    fun `tokenize with special characters`() {
        if (!this::tokenizer.isInitialized) return

        val prompt = "a cat, sitting on a chair!"

        val tokens = tokenizer.encode(prompt)

        assertEquals(77, tokens.size)
        assertEquals(49406, tokens[0])

        // Should handle punctuation
        assertTrue(tokens.any { it != 49406 && it != 49407 })

        println("Special chars prompt tokenized: ${tokens.take(15).joinToString()}")
    }

    @Test
    fun `verify max length parameter`() {
        if (!this::tokenizer.isInitialized) return

        val prompt = "a cat"

        // Test with custom max length
        val tokens50 = tokenizer.encode(prompt, maxLength = 50)
        val tokens100 = tokenizer.encode(prompt, maxLength = 100)

        assertEquals(50, tokens50.size)
        assertEquals(100, tokens100.size)

        // Both should start the same way
        assertEquals(tokens50[0], tokens100[0])
    }
}
