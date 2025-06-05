#ifndef GGML_H
#define GGML_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stddef.h>
#include <stdint.h>

struct ggml_context {
    void* mem_buffer;
    size_t mem_size;
};

struct ggml_tensor;

struct ggml_tensor* ggml_new_tensor_1d(struct ggml_context* ctx, int type, int size);

#ifdef __cplusplus
}
#endif
#endif // GGML_H
