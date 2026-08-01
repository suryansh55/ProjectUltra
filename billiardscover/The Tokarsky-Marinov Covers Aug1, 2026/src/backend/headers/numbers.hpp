#pragma once

#include <boost/multiprecision/gmp.hpp>

using CodeNumber = int32_t;

// The following typedefs are used for coefficients of equations
using Coeff16 = int16_t;
using Coeff32 = int32_t;
using Coeff64 = int64_t;

using Integer = boost::multiprecision::mpz_int;

using Rational = boost::multiprecision::mpq_rational;
