#pragma once

#include <atomic>
#include <chrono>
#include <iostream>
#include <sstream>
#include <string>
#include <thread>

class Progress final {
  private:
    std::atomic_uint64_t counter;
    std::thread thrd;

    static void pad_right(std::string& str, const size_t len, const char pad) {

        while (str.size() < len) {
            str.push_back(pad);
        }
    }

    static std::string format_counter(const uint64_t counter, const uint64_t total) {

        std::ostringstream oss{};

        oss << counter << '/' << total;

        auto str = oss.str();

        pad_right(str, 15, ' ');

        return str;
    }

    static std::string format_time(const std::chrono::steady_clock::duration& diff) {

        const auto hours = std::chrono::duration_cast<std::chrono::hours>(diff).count();
        const auto minutes = std::chrono::duration_cast<std::chrono::minutes>(diff).count() % 60;
        const auto seconds = std::chrono::duration_cast<std::chrono::seconds>(diff).count() % 60;
        const auto micros = std::chrono::duration_cast<std::chrono::microseconds>(diff).count() % 1000;

        std::ostringstream oss{};
        if (hours != 0) {
            oss << hours << "h ";
        }

        if (minutes != 0) {
            oss << minutes << "m ";
        }

        oss << seconds << '.' << micros << 's';

        auto str = oss.str();

        pad_right(str, 20, ' ');

        return str;
    }

    static void run(std::string msg, const uint64_t total, std::atomic_uint64_t& counter) {

        using namespace std::chrono_literals;

        const auto start = std::chrono::steady_clock::now();

        pad_right(msg, 20, ' ');

        while (counter != total) {

            const auto now = std::chrono::steady_clock::now();

            const auto count = format_counter(counter, total);
            const auto time = format_time(now - start);

            std::cout << '\r' << msg << count << time << std::flush;

            std::this_thread::sleep_for(1s);
        }

        // Print again at the very end to show we are done
        const auto now = std::chrono::steady_clock::now();

        const auto count = format_counter(counter, total);
        const auto time = format_time(now - start);

        std::cout << '\r' << msg << count << time << std::flush;

        // One final newline at the very end
        std::cout << std::endl;
    }

  public:
    explicit Progress(const std::string& msg, const uint64_t total)
        : counter{0},
          thrd{run, msg, total, std::ref(counter)} {}

    void operator++() {
        ++counter;
    }

    ~Progress() {
        thrd.join();
    }
};
