#pragma once

#include <mpfr.h>

#include "general.hpp"

class Evaluator {
  private:
    mpz_t quot;
    mpz_t rem;

    mpq_t xq;
    mpq_t yq;
    mpq_t argq; // In theory we can remove this variable, but it is very helpful for clarity

    mpfr_t term;
    mpfr_t sum;

    mpfr_t half_pi_d;
    mpfr_t half_pi_u;

  public:
    explicit Evaluator(const uint32_t prec);

    ~Evaluator();

    template <template <typename> class Trig>
    bool is_positive(const EqVec<Trig>& eq, const Coeff64 bound,
                     const PointQ& center, const Rational& radius);

    template <template <typename> class Trig>
    bool is_positive(const EqVec<Trig>& eq, const Coeff64 bx, const Coeff64 by,
                     const PointQ& center, const Rational& rx, const Rational& ry);

    template <template <typename> class Trig>
    bool is_positive(const EqMap<Trig>& eq, const Coeff64 bx, const Coeff64 by,
                     const PointQ& center, const Rational& rx, const Rational& ry);
};

extern template bool Evaluator::is_positive(const EqVec<Sin>& eq, const Coeff64 bound,
                                            const PointQ& center, const Rational& radius);
extern template bool Evaluator::is_positive(const EqVec<Cos>& eq, const Coeff64 bound,
                                            const PointQ& center, const Rational& radius);

extern template bool Evaluator::is_positive(const EqVec<Sin>& eq, const Coeff64 bx, const Coeff64 by,
                                            const PointQ& center, const Rational& rx, const Rational& ry);
extern template bool Evaluator::is_positive(const EqVec<Cos>& eq, const Coeff64 bx, const Coeff64 by,
                                            const PointQ& center, const Rational& rx, const Rational& ry);

extern template bool Evaluator::is_positive(const EqMap<Sin>& eq, const Coeff64 bx, const Coeff64 by,
                                            const PointQ& center, const Rational& rx, const Rational& ry);
extern template bool Evaluator::is_positive(const EqMap<Cos>& eq, const Coeff64 bx, const Coeff64 by,
                                            const PointQ& center, const Rational& rx, const Rational& ry);
