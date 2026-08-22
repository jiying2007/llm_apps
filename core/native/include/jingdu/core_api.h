#pragma once

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct JingduCoreAbiVersion {
    uint32_t major;
    uint32_t minor;
    uint32_t patch;
} JingduCoreAbiVersion;

JingduCoreAbiVersion jingdu_core_abi_version(void);

#ifdef __cplusplus
}
#endif
