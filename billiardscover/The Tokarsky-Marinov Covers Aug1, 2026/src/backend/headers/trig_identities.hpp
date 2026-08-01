#pragma once

#include "general.hpp"

std::pair<Coeff32, Sin<LinComArrZ<XY>>> simplify_sin_xy(const LinComArrZ<XY>& arg);

std::pair<Coeff32, Cos<LinComArrZ<XY>>> simplify_cos_xy(const LinComArrZ<XY>& arg);

std::pair<Coeff32, Sin<LinComArrZ<XY>>> simplify_sin_xypi(const LinComArrZ<XYPi>& arg);

std::pair<Coeff32, Cos<LinComArrZ<XY>>> simplify_cos_xypi(const LinComArrZ<XYPi>& arg);

std::pair<Coeff32, Cos<LinComArrZ<XY>>> simplify_sin_xyeta(const LinComArrZ<XYEta>& arg);

std::pair<Coeff32, Sin<LinComArrZ<XY>>> simplify_cos_xyeta(const LinComArrZ<XYEta>& arg);

LinComMapZ<Sin<LinComArrZ<XY>>> multiply_lin_com(const LinComMapZ<Cos<LinComArrZ<XY>>>& lin_com_cos, const LinComMapZ<Sin<LinComArrZ<XY>>>& lin_com_sin);

LinComMapZ<Cos<LinComArrZ<XY>>> multiply_lin_com(const LinComMapZ<Sin<LinComArrZ<XY>>>& lin_com_sin1, const LinComMapZ<Sin<LinComArrZ<XY>>>& lin_com_sin2);

LinComMapZ<Cos<LinComArrZ<XY>>> multiply_lin_com(const LinComMapZ<Cos<LinComArrZ<XY>>>& lin_com_cos1, const LinComMapZ<Cos<LinComArrZ<XY>>>& lin_com_cos2);
