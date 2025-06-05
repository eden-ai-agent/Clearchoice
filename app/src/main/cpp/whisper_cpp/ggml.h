// Minimal placeholder ggml.h for whisper.cpp compilation
#ifndef GGML_H
#define GGML_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

/* Forward declarations used by function prototypes below.  Declaring them
 * early avoids potential visibility warnings with some compilers when the
 * full definitions appear later in the file. */
struct ggml_context;
struct ggml_init_params;

#ifdef __cplusplus
extern "C" {
#endif

// Minimal context structure - real ggml is much more complex
struct ggml_context {
    void * mem_buffer;  // pointer to allocated memory buffer
    size_t mem_size;    // size of the buffer in bytes
};

// Parameters for ggml_init mirroring the real library
struct ggml_init_params {
    size_t  mem_size;   // size of the memory buffer to use
    void  * mem_buffer; // buffer to use for memory allocation
    bool    no_alloc;   // if true, do not allocate memory if mem_buffer is NULL
};

// Placeholder for tensor structure
struct ggml_tensor {
    int32_t type; // Placeholder for ggml_type
    int32_t backend; // Placeholder for ggml_backend_type
    void * data;
    size_t ne[4]; // Number of elements in each dimension
    // ... other fields as minimally required by whisper.h/cpp interfaces
};


// Minimal type definitions needed by whisper.h/cpp
enum ggml_type {
    GGML_TYPE_F32,
    GGML_TYPE_F16,
    GGML_TYPE_Q4_0,
    GGML_TYPE_Q4_1,
    GGML_TYPE_Q5_0,
    GGML_TYPE_Q5_1,
    GGML_TYPE_Q8_0,
    GGML_TYPE_Q8_1,
    // ... other types
    GGML_TYPE_I32,
};

// Example function signatures that might be used by whisper.cpp
// These are just placeholders.
struct ggml_context * ggml_init(struct ggml_init_params params);
void ggml_free(struct ggml_context * ctx);

// Add any other minimal ggml structures or functions that whisper.h/cpp might expect.
// For example, if whisper.cpp calls ggml_new_tensor_1d, its signature would be here.

#ifdef __cplusplus
}
#endif

#endif // GGML_H
