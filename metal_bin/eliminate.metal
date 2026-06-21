// Suryansh Ankur, 2026
#include <metal_stdlib>
using namespace metal;

// 1. Memory layouts matching your C++ LinComArrZ (64-bit ints)
struct EquationXYEta {
    long x_coeff;
    long y_coeff;
    long eta_coeff;
};

struct EquationXYEtaPhi {
    long x_coeff;
    long y_coeff;
    long eta_coeff;
    long phi_coeff;
};

// 2. Custom Math Helpers for the GPU
long custom_abs(long a) {
    return a < 0 ? -a : a;
}

long custom_gcd(long a, long b) {
    a = custom_abs(a);
    b = custom_abs(b);
    while (b != 0) {
        long temp = b;
        b = a % b;
        a = temp;
    }
    return a;
}

// 3. The Core Compute Kernel
kernel void eliminate_phi_kernel(
    device const EquationXYEtaPhi* positive_phi [[buffer(0)]],
    device const EquationXYEtaPhi* negative_phi [[buffer(1)]],
    device EquationXYEta* output_buffer [[buffer(2)]],
    device atomic_uint* output_counter [[buffer(3)]],
    uint2 grid_pos [[thread_position_in_grid]],
    uint2 grid_size [[threads_per_grid]]
) {
    // grid_pos.x is our positive_phi index
    // grid_pos.y is our negative_phi index
    
    // Bounds checking
    if (grid_pos.x >= grid_size.x || grid_pos.y >= grid_size.y) {
        return;
    }

    EquationXYEtaPhi pos_eq = positive_phi[grid_pos.x];
    EquationXYEtaPhi neg_eq = negative_phi[grid_pos.y];
    
    long new_phi = pos_eq.phi_coeff + neg_eq.phi_coeff;
    
    // Your exact remove_phi logic
    if (new_phi == 0) {
        long new_x = pos_eq.x_coeff + neg_eq.x_coeff;
        long new_y = pos_eq.y_coeff + neg_eq.y_coeff;
        long new_eta = pos_eq.eta_coeff + neg_eq.eta_coeff;
        
        // Your exact divide_content logic
        long g = custom_gcd(new_x, custom_gcd(new_y, new_eta));
        if (g > 1) {
            new_x /= g;
            new_y /= g;
            new_eta /= g;
        }
        
        // Atomically claim the next available index in the flat output buffer
        uint index = atomic_fetch_add_explicit(output_counter, 1, memory_order_relaxed);
        
        // Save the result directly to GPU RAM
        output_buffer[index].x_coeff = new_x;
        output_buffer[index].y_coeff = new_y;
        output_buffer[index].eta_coeff = new_eta;
    }
}