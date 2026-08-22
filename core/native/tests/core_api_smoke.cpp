#include "jingdu/core_api.h"

int main() {
    const JingduCoreAbiVersion version = jingdu_core_abi_version();
    return version.major == 1U ? 0 : 1;
}
