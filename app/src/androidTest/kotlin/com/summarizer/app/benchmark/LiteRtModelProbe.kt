package com.summarizer.app.benchmark

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Experiment helper: lists LiteRT-LM (.litertlm) models on Hugging Face as seen from the phone's
 * network, with gating and file sizes. Metadata only; nothing is downloaded. Tag "LiteRtProbe".
 */
@RunWith(AndroidJUnit4::class)
class LiteRtModelProbe {

    private val client = OkHttpClient()

    @Test
    fun listLiteRtModels() {
        val searches = listOf(
            "https://huggingface.co/api/models?author=litert-community&limit=200",
            "https://huggingface.co/api/models?author=google&search=litert&limit=100"
        )
        val ids = searches.flatMap { url ->
            JsonParser.parseString(get(url)).asJsonArray.map { it.asJsonObject["id"].asString }
        }.distinct().filter { id ->
            listOf("gemma", "qwen", "llama", "phi", "smol").any { it in id.lowercase() }
        }
        log("Candidates: ${ids.size}")

        for (id in ids) {
            runCatching {
                val model = JsonParser.parseString(get("https://huggingface.co/api/models/$id?blobs=true")).asJsonObject
                val gated = model["gated"]?.toString() ?: "false"
                val files = model["siblings"]?.asJsonArray.orEmpty().map { it.asJsonObject }
                    .filter { it["rfilename"].asString.endsWith(".litertlm") }
                    .joinToString { f ->
                        val sizeMb = (f["size"]?.asLong ?: 0L) / 1_000_000
                        "${f["rfilename"].asString} (${sizeMb} MB)"
                    }
                if (files.isNotEmpty()) log("$id gated=$gated sha=${model["sha"]?.asString} files=[$files]")
            }.onFailure { log("$id FAILED: ${it.message}") }
        }
    }

    /** Logs commit and SHA-256 of model files: -e specs "repo|file;repo|file" (pins the catalog). */
    @Test
    fun fileHashes() {
        val specs = InstrumentationRegistry.getArguments().getString("specs") ?: return
        specs.split(";").map { it.split("|") }.forEach { (repo, file) ->
            runCatching {
                val model = JsonParser.parseString(get("https://huggingface.co/api/models/$repo?blobs=true")).asJsonObject
                val sibling = model["siblings"].asJsonArray.map { it.asJsonObject }.first { it["rfilename"].asString == file }
                val lfs = sibling["lfs"].asJsonObject
                log("HASH $repo|$file|${model["sha"].asString}|${lfs["sha256"].asString}|${lfs["size"].asLong}")
            }.onFailure { log("HASH $repo|$file FAILED: ${it.message}") }
        }
    }

    private fun get(url: String): String =
        client.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }

    private fun com.google.gson.JsonArray?.orEmpty() = this?.toList() ?: emptyList()

    private fun log(message: String) {
        Log.i("LiteRtProbe", message)
    }
}
