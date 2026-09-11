#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace epsilon::smtc {

struct Snapshot {
    bool available = false;
    std::wstring title;
    std::wstring artist;
    std::wstring album_title;
    std::wstring source_app_id;
    int playback_status = 0;
    std::uint64_t thumbnail_revision = 0;
    std::optional<std::vector<std::uint8_t>> thumbnail;
    std::wstring error;
};

Snapshot poll();
void reset();

} // namespace epsilon::smtc
