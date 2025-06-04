#ifndef WHISPER_H
#define WHISPER_H

#include "ggml.h" // Assuming ggml.h is in the same directory or include path

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef WHISPER_SHARED
#    ifdef _WIN32
#        ifdef WHISPER_BUILD
#            define WHISPER_API __declspec(dllexport)
#        else
#            define WHISPER_API __declspec(dllimport)
#        endif
#    else
#        define WHISPER_API __attribute__ ((visibility ("default")))
#    endif
#else
#    define WHISPER_API
#endif

#ifdef __cplusplus
extern "C" {
#endif

// Forward declarations
struct whisper_context;
struct whisper_state; // Opaque structure for whisper state

// Audio format parameters
#define WHISPER_SAMPLE_RATE 16000
#define WHISPER_N_FFT       400
#define WHISPER_N_MEL       80
#define WHISPER_HOP_LENGTH  160
#define WHISPER_CHUNK_SIZE  30

//
// C interface
//

// Available sampling strategies
enum whisper_sampling_strategy {
    WHISPER_SAMPLING_GREEDY,      // similar to Beam Search with B=1
    WHISPER_SAMPLING_BEAM_SEARCH, // TODO: not implemented
};


struct whisper_context_params {
    bool use_gpu; // Not typically used in mobile builds without GPU support for ggml
    // Add other params as needed if whisper_init_from_file_with_params is used
};

// Initialize the whisper context from a model file.
// Returns NULL on failure.
WHISPER_API struct whisper_context * whisper_init_from_file_with_params(const char * path_model, struct whisper_context_params params);
WHISPER_API struct whisper_context * whisper_init_from_file(const char * path_model); // Simplified version

// Frees all memory allocated by the whisper context.
WHISPER_API void whisper_free(struct whisper_context * ctx);


// Callback types
typedef void (*whisper_new_segment_callback) (struct whisper_context * ctx, struct whisper_state * state, int n_new, void * user_data);
// ... other callbacks if needed

// Parameters for the whisper_full() function.
// If you change the order or add new parameters, make sure to update the default values in whisper.cpp:
// whisper_full_default_params()
struct whisper_full_params {
    enum whisper_sampling_strategy strategy;

    int n_threads;
    int n_max_text_ctx; // max tokens to use from past text as prompt for the decoder
    int offset_ms;      // start offset in ms
    int duration_ms;    // audio duration to process in ms

    bool translate;
    bool no_context;    // do not use past transcription (if any) as initial prompt for the decoder
    bool single_segment; // force single segment output (useful for streaming)
    bool print_special; // print special tokens (e.g. <SOT>, <EOT>, <BEG>, etc.)
    bool print_progress; // print progress information
    bool print_realtime; // print results from within whisper.cpp (useful for debugging)
    bool print_timestamps; // print timestamps for each segment

    // [EXPERIMENTAL] token-level timestamps
    bool  token_timestamps; // enable token-level timestamps
    float thold_pt;       // timestamp token probability threshold
    float thold_ptsum;    // timestamp token sum probability threshold
    int   max_len;        // max segment length in characters
    bool  split_on_word;  // split on word rather than on token (when max_len is reached)
    int   max_tokens;     // max tokens per segment (0 = no limit)

    // [EXPERIMENTAL] speed-up techniques
    // TODO: not implemented
    bool speed_up;        // speed-up techniques by 2x using past_kv_cache

    // [EXPERIMENTAL]
    // TODO: not implemented
    int audio_ctx; // overwrite the audio context size (0 = use default)

    const char * language; // "en", "es", etc. use "auto" for auto-detection.
                           // multilingual models only.

    // Callbacks
    void * user_data;
    whisper_new_segment_callback new_segment_callback;
    // ... other callback fields
};

// Initialize whisper_full_params with default values
WHISPER_API struct whisper_full_params whisper_full_default_params(enum whisper_sampling_strategy strategy);

// Run the entire model: PCM -> log-mel spectrogram -> encoder -> decoder -> text
// Not thread safe for a single context
// Uses the specified decoding strategy to obtain the text.
WHISPER_API int whisper_full(
        struct whisper_context * ctx,
        struct whisper_full_params params,
        const float * samples,
        int   n_samples);

// Get the number of text segments.
WHISPER_API int whisper_full_n_segments(struct whisper_context * ctx);

// Get the text of a segment.
WHISPER_API const char * whisper_full_get_segment_text(struct whisper_context * ctx, int i_segment);

// Get the start and end time of a segment.
WHISPER_API int64_t whisper_full_get_segment_t0(struct whisper_context * ctx, int i_segment);
WHISPER_API int64_t whisper_full_get_segment_t1(struct whisper_context * ctx, int i_segment);

// Language functions (for multilingual models)
WHISPER_API int whisper_lang_auto_detect(
        struct whisper_context * ctx,
        int   offset_ms,
        int   n_threads,
        float * lang_probs); // Realtime probability for each language
WHISPER_API const char * whisper_lang_str(int lang_id); // Convert lang_id to string


#ifdef __cplusplus
}
#endif

#endif // WHISPER_H
