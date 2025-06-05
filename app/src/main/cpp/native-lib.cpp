#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <thread>
#include "llama.h"
#include "common/sampling.h"
#include "json.hpp"
#include <ctime>

#define LOG_TAG "LlamaJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

using json = nlohmann::json;

struct SafeBatch {
    llama_batch batch;
    bool initialized = false;

    SafeBatch(int32_t n_tokens, int32_t embd, int32_t n_seq_max) : batch{} {
        if (static_cast<size_t>(n_tokens) > INT32_MAX) {
            LOGE("n_tokens exceeds INT32_MAX, capping to INT32_MAX");
            n_tokens = INT32_MAX;
        }
        batch = llama_batch_init(n_tokens, embd, n_seq_max);
        initialized = (batch.token != nullptr);
    }

    ~SafeBatch() {
        if (initialized) {
            llama_batch_free(batch);
        }
    }
};

std::string sanitize_json(const std::string& response) {
    std::string output = response;
    output.erase(std::remove_if(output.begin(), output.end(),
                                [](char c) { return c == '<' || c == '|' || c == '\n'; }), output.end());
    size_t start = output.find_first_of('{');
    size_t brace_count = 0;
    size_t end = std::string::npos;
    for (size_t i = start; i < output.size(); ++i) {
        if (output[i] == '{') brace_count++;
        if (output[i] == '}') brace_count--;
        if (brace_count == 0 && output[i] == '}') {
            end = i;
            break;
        }
    }

    if (start != std::string::npos && end != std::string::npos && end > start) {
        try {
            std::string candidate = output.substr(start, end - start + 1);
            auto json_result = json::parse(candidate);
            if (json_result.is_object() && (json_result.contains("intent") || json_result.contains("response"))) {
                return json_result.dump();
            }
        } catch (...) {
            LOGI("Invalid JSON segment: %s", output.substr(start, end - start + 1).c_str());
        }
    }
    json fallback = {{"error", "invalid_json_output"}, {"data", output}};
    return fallback.dump();
}

std::string getCurrentDate() {
    std::time_t now = std::time(nullptr);
    std::tm* local = std::localtime(&now);
    char buffer[11];
    std::strftime(buffer, sizeof(buffer), "%Y-%m-%d", local);
    return std::string(buffer);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_myapplication_MainActivity_runLlama(
        JNIEnv* env, jobject, jstring modelPath, jstring prompt) {

    char piece[128];

    llama_backend_init(true);

    const char* model_file = env->GetStringUTFChars(modelPath, nullptr);
    const char* input_text = env->GetStringUTFChars(prompt, nullptr);
    LOGI("Loading model: %s with prompt: %s", model_file, input_text);

    struct llama_model_params model_params = llama_model_default_params();
    model_params.use_mmap = true;
    model_params.use_mlock = false;

    struct llama_model* model = nullptr;
    try {
        model = llama_model_load_from_file(model_file, model_params);
    } catch (...) {
        LOGE("Model load failed");
        llama_backend_free();
        env->ReleaseStringUTFChars(modelPath, model_file);
        env->ReleaseStringUTFChars(prompt, input_text);
        return env->NewStringUTF("{\"error\":\"model_load_failed\"}");
    }
    if (!model) {
        LOGE("Model load failed");
        llama_backend_free();
        env->ReleaseStringUTFChars(modelPath, model_file);
        env->ReleaseStringUTFChars(prompt, input_text);
        return env->NewStringUTF("{\"error\":\"model_load_failed\"}");
    }
    LOGI("Model load time: %ld ms", llama_time_us() / 1000);

    const struct llama_vocab* vocab = llama_model_get_vocab(model);
    int vocab_size = llama_vocab_n_tokens(vocab);
    LOGI("Vocabulary size: %d, BOS token: %d, EOS token: %d",
         vocab_size, llama_vocab_bos(vocab), llama_vocab_eos(vocab));

    struct llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 1024;
    ctx_params.n_threads = std::min(3, (int)std::thread::hardware_concurrency());
    ctx_params.n_threads_batch = ctx_params.n_threads;
    LOGI("Using n_ctx: %d, threads: %d", ctx_params.n_ctx, ctx_params.n_threads);

    struct llama_context* ctx = llama_init_from_model(model, ctx_params);
    if (!ctx) {
        LOGE("Context creation failed");
        llama_model_free(model);
        llama_backend_free();
        env->ReleaseStringUTFChars(modelPath, model_file);
        env->ReleaseStringUTFChars(prompt, input_text);
        return env->NewStringUTF("{\"error\":\"context_init_failed\"}");
    }

    std::string formatted_prompt =
            "<|begin_of_text|><|start_header_id|>system<|end_header_id>\n"
            "You are a helpful assistant that responds with a single valid JSON object. "
            "For scheduling requests, return: {\"intent\": \"schedule\", \"details\": {\"event\": \"event name\", \"time\": \"HH:mm\", \"date\": \"yyyy-MM-dd\"}}. "
            "For reminder requests, return: {\"intent\": \"reminder\", \"details\": {\"title\": \"title\", \"time\": \"HH:mm\", \"date\": \"yyyy-MM-dd\", \"method\": \"notification\"}}. "
            "For alarm requests, return: {\"intent\": \"alarm\", \"details\": {\"label\": \"label\", \"time\": \"HH:mm\", \"date\": \"yyyy-MM-dd\"}}. "
            "For note requests, return: {\"intent\": \"note\", \"details\": {\"title\": \"note_title\", \"content\": \"content\", \"date\": \"yyyy-MM-dd\"}}. "
            "For call requests, return: {\"intent\": \"call\", \"details\": {\"number\": \"phone_number\"}}. "
            "For SMS requests, return: {\"intent\": \"sms\", \"details\": {\"number\": \"phone_number\", \"message\": \"message_text\"}}. "
            "For creating Excel file requests, return: {\"intent\": \"create_excel\", \"details\": {\"filename\": \"filename.xlsx\", \"data\": [{\"column1\": \"value1\", \"column2\": \"value2\"}, ...]}}. "
            "For deleting Excel file requests, return: {\"intent\": \"delete_excel\", \"details\": {\"filename\": \"filename.xlsx\"}}. "
            "For reading Excel file requests, return: {\"intent\": \"read_excel\", \"details\": {\"filename\": \"filename.xlsx\"}}. "
            "For greetings like 'hello', 'hi', or similar vague inputs, return: {\"response\": \"Hey, how can I help you?\"}. "
            "For other requests, return: {\"response\": \"text\"}. "
            "Use the current date, " + getCurrentDate() + ", to calculate dates (e.g., \"tomorrow\" is one day after). "
                                                          "For note requests like 'create a note <title> \"content\"', set the 'title' field to <title> and 'content' to the quoted content. "
                                                          "Do not include extra text, newlines, chat template tokens (e.g., <|eot_id|>), or escaped slashes in the JSON.\n"
                                                          "<|eot_id|><|start_header_id|>user<|end_header_id>\n"
            + std::string(input_text) +
            "\n<|eot_id|><|start_header_id|>assistant<|end_header_id>\n";

    LOGI("Formatted prompt:\n%s", formatted_prompt.c_str());

    std::vector<llama_token> prompt_tokens(llama_n_ctx(ctx));
    int32_t prompt_token_count = llama_tokenize(
            vocab,
            formatted_prompt.c_str(),
            (int32_t) formatted_prompt.size(),
            prompt_tokens.data(),
            (int32_t) prompt_tokens.size(),
            true,
            false
    );

    if (prompt_token_count < 0) {
        LOGE("Tokenization failed");
        llama_free(ctx);
        llama_model_free(model);
        llama_backend_free();
        env->ReleaseStringUTFChars(modelPath, model_file);
        env->ReleaseStringUTFChars(prompt, input_text);
        return env->NewStringUTF("{\"error\":\"tokenization_failed\"}");
    }

    if (prompt_token_count >= ctx_params.n_ctx) {
        LOGE("Tokenized prompt too long: %d >= %d", prompt_token_count, ctx_params.n_ctx);
        llama_free(ctx);
        llama_model_free(model);
        llama_backend_free();
        env->ReleaseStringUTFChars(modelPath, model_file);
        env->ReleaseStringUTFChars(prompt, input_text);
        return env->NewStringUTF("{\"error\":\"prompt_too_long\"}");
    }

    std::vector<llama_token> tokens(prompt_tokens.begin(), prompt_tokens.begin() + prompt_token_count);
    int32_t tokenized = (int32_t) tokens.size();
    LOGI("Input prompt: %s", input_text);
    LOGI("Tokenized prompt into %d tokens", tokenized);

    SafeBatch batch(tokenized, 0, 1);
    if (!batch.initialized) {
        LOGE("Batch init failed");
        llama_free(ctx);
        llama_model_free(model);
        env->ReleaseStringUTFChars(modelPath, model_file);
        env->ReleaseStringUTFChars(prompt, input_text);
        return env->NewStringUTF("{\"error\":\"batch_init_failed\"}");
    }
    for (int i = 0; i < tokenized; ++i) {
        batch.batch.token[i] = tokens[i];
        batch.batch.pos[i] = i;
        batch.batch.n_seq_id[i] = 1;
        batch.batch.seq_id[i] = new llama_seq_id[1]{0};
        batch.batch.logits[i] = (i == tokenized - 1);
    }
    batch.batch.n_tokens = tokenized;

    int decode_result = llama_decode(ctx, batch.batch);
    LOGI("Initial decode result: %d", decode_result);
    if (decode_result != 0) {
        LOGE("Initial decode failed");
        for (int i = 0; i < tokenized; ++i) {
            if (batch.batch.seq_id[i]) {
                delete[] batch.batch.seq_id[i];
                batch.batch.seq_id[i] = nullptr;
            }
        }
        llama_free(ctx);
        llama_model_free(model);
        env->ReleaseStringUTFChars(modelPath, model_file);
        env->ReleaseStringUTFChars(prompt, input_text);
        return env->NewStringUTF("{\"error\":\"initial_decode_failed\"}");
    }

    for (int i = 0; i < tokenized; ++i) {
        if (batch.batch.seq_id[i]) {
            delete[] batch.batch.seq_id[i];
            batch.batch.seq_id[i] = nullptr;
        }
    }

    std::string response;
    const int max_tokens = 100;
    int generated = 0;
    const llama_token eos_token = llama_vocab_eos(vocab);

    SafeBatch gen_batch(1, 0, 1);
    if (!gen_batch.initialized) {
        LOGE("Gen batch init failed");
        llama_free(ctx);
        llama_model_free(model);
        llama_backend_free();
        env->ReleaseStringUTFChars(modelPath, model_file);
        env->ReleaseStringUTFChars(prompt, input_text);
        return env->NewStringUTF("{\"error\":\"gen_batch_failed\"}");
    }

    struct llama_sampler* sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.94f, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.5f));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(42));

    int batch_size = 0;
    int64_t start_time = llama_time_us();
    while (generated < max_tokens) {
        float* logits = llama_get_logits(ctx);
        if (!logits) {
            LOGE("Logits retrieval failed");
            response = "{\"error\":\"logits_failed\"}";
            break;
        }

        llama_token id = llama_sampler_sample(sampler, ctx, -1);
        LOGI("Token ID: %d", id);
        if (id == eos_token) {
            LOGI("EOS reached");
            break;
        }

        int len = llama_token_to_piece(vocab, id, piece, sizeof(piece), 0, false);
        if (len <= 0 || len >= sizeof(piece)) {
            LOGE("Token conversion failed or buffer overflow for ID: %d", id);
            response = "{\"error\":\"token_conversion_failed\"}";
            break;
        }
        piece[len] = '\0';

        std::string sanitized_piece(piece, len);
        if (sanitized_piece.empty()) {
            LOGE("Sanitized piece is empty for token ID: %d", id);
            continue;
        }
        response.append(sanitized_piece);
        if (generated < 5) LOGI("Generated: %s", sanitized_piece.c_str());

        if (sanitized_piece.find('}') != std::string::npos) {
            try {
                json::parse(response);
                LOGI("Valid JSON detected: %s", response.c_str());
                break;
            } catch (...) {
                LOGI("Incomplete JSON, continuing");
            }
        }

        gen_batch.batch.token[batch_size] = id;
        gen_batch.batch.pos[batch_size] = tokenized + generated;
        gen_batch.batch.n_seq_id[batch_size] = 1;
        gen_batch.batch.seq_id[batch_size] = new llama_seq_id[1]{0};
        gen_batch.batch.logits[batch_size] = true;
        batch_size++;
        generated++;

        if (batch_size == 1 || generated == max_tokens) {
            gen_batch.batch.n_tokens = batch_size;
            int decode_result = llama_decode(ctx, gen_batch.batch);
            if (decode_result != 0) {
                LOGE("Batch decode failed: %d", decode_result);
                response = "{\"error\":\"decode_failed\"}";
                break;
            }
            for (int i = 0; i < batch_size; ++i) {
                delete[] gen_batch.batch.seq_id[i];
                gen_batch.batch.seq_id[i] = nullptr;
            }
            batch_size = 0;
        }
        llama_sampler_accept(sampler, id);
    }

    for (int i = 0; i < batch_size; ++i) {
        delete[] gen_batch.batch.seq_id[i];
    }

    std::string final_output = sanitize_json(response);
    LOGI("Generation time: %ld ms", (llama_time_us() - start_time) / 1000);
    LOGI("Raw response: %s", response.c_str());
    LOGI("Final JSON: %s", final_output.c_str());

    llama_sampler_free(sampler);
    llama_free(ctx);
    llama_model_free(model);
    llama_backend_free();
    env->ReleaseStringUTFChars(modelPath, model_file);
    env->ReleaseStringUTFChars(prompt, input_text);
    return env->NewStringUTF(final_output.c_str());
}


