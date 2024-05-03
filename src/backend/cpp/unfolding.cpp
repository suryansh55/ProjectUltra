#include "unfolding.hpp"
#include "conversion.hpp"
#include "division.hpp"
#include "trig_identities.hpp"

static std::vector<Vertex> find_path(const Vertex& start, const Vertex& end) {

    std::vector<Vertex> path{};

    // if it is on the outside, move back into the main thing
    if (start.branch != 0) {
        path.push_back(start);
    }

    // now we move up or down until we get there
    // at most one of the following two loops will execute
    // this will add (start.number, Position.MAIN) for us already
    for (auto i = start.number; i < end.number; ++i) {
        path.emplace_back(i, 0);
    }

    for (auto i = start.number; i > end.number; --i) {
        path.emplace_back(i, 0);
    }

    // move over to the end
    path.emplace_back(end.number, 0);

    // move out on a branch if necessary
    if (end.branch != 0) {
        path.push_back(end);
    }

    return path;
}

template <typename T>
std::vector<T> double_if_odd(const std::vector<T>& vec) {

    std::vector<T> copy = vec;

    if (vec.size() % 2 != 0) {
        copy.insert(std::end(copy), std::begin(vec), std::end(vec));
    }

    return copy;
}

// TODO we need to change the orientation of the vertices so they are flipped around
Unfolding::Unfolding(const std::vector<CodeNumber>& tmp_code_numbers, const std::vector<XYZ>& tmp_code_angles) {

    const auto code_numbers = double_if_odd(tmp_code_numbers);
    const auto code_angles = double_if_odd(tmp_code_angles);

    // We could just use back and front, but those are undefined when code_angles
    // is empty. Safety first.
    const auto prev_angle = code_angles.at(code_angles.size() - 1);
    const auto next_angle = code_angles.at(0);
    const auto current_side = other_angle(prev_angle, next_angle);

    const Vertex first_vertex{1, 0};
    const Vertex second_vertex{2, 0};

    right_vertices.push_back(first_vertex);
    left_vertices.push_back(second_vertex);

    // 2 <- 1
    const LinComArrZ<XYPi> pi{0, 0, 1};
    // Butt ugly, but hey, that's C++
    edges.emplace(std::make_pair(std::make_pair(first_vertex, second_vertex), Edge{current_side, pi}));

    // 2 -> 1
    const LinComArrZ<XYPi> zero{};
    edges.emplace(std::make_pair(std::make_pair(second_vertex, first_vertex), Edge{current_side, zero}));

    // the angle going backwards along the chain, so from 2 -> 1 in this case
    LinComArrZ<XYPi> prev_polar_angle{};

    const auto size = code_numbers.size();

    // really should use a zip in this case
    for (size_t i = 0; i < size; i += 1) {
        // Actually, unsigned integer overflow is defined to wrap
        // around, so that's ok. Still, this is more explicit
        const auto prev_code_angle = (i == 0) ? code_angles.at(code_angles.size() - 1) : code_angles.at(i - 1);
        const auto current_code_number = code_numbers.at(i);
        const auto current_code_angle = code_angles.at(i);
        const auto current_code_angle_pi = xyz_to_xypi(current_code_angle);

        const auto next_code_angle = code_angles.at((i + 1) % size);

        // if (i % 2) == 0, we are on the left hand side, so side = 1
        // otherwise, we are on the right hand side, so side = -1

        const CodeNumber side = (i % 2) == 0 ? 1 : -1;
        std::vector<Vertex>& add_vertices = (i % 2) == 0 ? right_vertices : left_vertices;

        // number of the current main vertex
        const size_t current_number = i + 2;

        // create the next main edge
        const Vertex current_main_vertex{current_number, 0};
        const Vertex next_main_vertex{current_number + 1, 0};
        const auto main_edge_type = other_angle(current_code_angle, next_code_angle);

        // current_main_vertex -> next_main_vertex
        // this must be a different variable from
        // prev polar angle, because we use the old value
        // of prev polar angle later on down
        LinComArrZ<XYPi> current_to_next_main_polar_angle{};
        current_to_next_main_polar_angle.add(prev_polar_angle);
        current_to_next_main_polar_angle.add(current_code_number * side, current_code_angle_pi);

        // current_main_vertex -> next_main_vertex
        edges.emplace(std::make_pair(std::make_pair(current_main_vertex, next_main_vertex), Edge{main_edge_type, current_to_next_main_polar_angle}));

        // next_main_vertex -> current_main_vertex
        // this variable name is now wrong, but whatevs
        current_to_next_main_polar_angle.add(XYPi::Pi);
        edges.emplace(std::make_pair(std::make_pair(next_main_vertex, current_main_vertex), Edge{main_edge_type, current_to_next_main_polar_angle}));

        // now add the branches

        // if current_code_number == 1, don't add any extra vertices
        if (current_code_number == 2) {
            // create a middle one
            // in this situation, prev_code_angle == next_code_angle, so it doesn't matter which one you use
            const Vertex middle_vertex{current_number, 1};

            LinComArrZ<XYPi> main_to_middle_polar_angle{};
            main_to_middle_polar_angle.add(prev_polar_angle);
            main_to_middle_polar_angle.add(side, current_code_angle_pi);

            // inserting makes a copy of the stuff
            // which is what we want, because we mutate it after
            edges.emplace(std::make_pair(std::make_pair(current_main_vertex, middle_vertex), Edge{prev_code_angle, main_to_middle_polar_angle}));

            // Again, the variable name is now incorrect
            main_to_middle_polar_angle.add(XYPi::Pi);
            edges.emplace(std::make_pair(std::make_pair(middle_vertex, current_main_vertex), Edge{prev_code_angle, main_to_middle_polar_angle}));

            add_vertices.push_back(middle_vertex);
        } else if (current_code_number >= 3) {
            // create upper and lower branches

            const Vertex lower_vertex{current_number, 1};

            LinComArrZ<XYPi> main_to_lower_polar_angle{};
            main_to_lower_polar_angle.add(prev_polar_angle);
            main_to_lower_polar_angle.add(side, current_code_angle_pi);

            edges.emplace(std::make_pair(std::make_pair(current_main_vertex, lower_vertex), Edge{prev_code_angle, main_to_lower_polar_angle}));

            // Again, the variable name is now wrong
            main_to_lower_polar_angle.add(XYPi::Pi);
            edges.emplace(std::make_pair(std::make_pair(lower_vertex, current_main_vertex), Edge{prev_code_angle, main_to_lower_polar_angle}));

            add_vertices.push_back(lower_vertex);

            // upper branch
            const Vertex upper_vertex{current_number, 2};
            LinComArrZ<XYPi> main_to_upper_polar_angle{};
            main_to_upper_polar_angle.add(prev_polar_angle);
            main_to_upper_polar_angle.add(side * (current_code_number - 1), current_code_angle_pi);

            edges.emplace(std::make_pair(std::make_pair(current_main_vertex, upper_vertex), Edge{next_code_angle, main_to_upper_polar_angle}));

            // Again, variable name is now wrong
            main_to_upper_polar_angle.add(XYPi::Pi);
            edges.emplace(std::make_pair(std::make_pair(upper_vertex, current_main_vertex), Edge{next_code_angle, main_to_upper_polar_angle}));

            add_vertices.push_back(upper_vertex);
        }

        add_vertices.push_back(next_main_vertex);

        // we already added Pi above, so we don't need
        // to do it here
        prev_polar_angle = current_to_next_main_polar_angle;
    }
}

// given an unfolding and a path from one point to another, this gives the vector going from the first point to the second.
// The first coordinate is x, the second y
std::pair<Equation<Sin>, Equation<Cos>> Unfolding::path_vector(const std::vector<Vertex>& path) const {

    //std::vector<std::pair<Sin<XYZ>, Cos<LinComArrZ<XYPi>>>> xs{};
    //std::vector<std::pair<Sin<XYZ>, Sin<LinComArrZ<XYPi>>>> ys{};

    Equation<Sin> coord_x{};
    Equation<Cos> coord_y{};

    for (size_t i = 0; i + 1 < path.size(); ++i) {

        const Vertex current_vertex = path.at(i);
        const Vertex next_vertex = path.at(i + 1);

        const Edge& current_edge = edges.at({current_vertex, next_vertex});

        //xs.emplace_back(Sin<XYZ>{current_edge.edge_type}, Cos<LinComArrZ<XYPi>>{current_edge.polar_angle});
        //ys.emplace_back(Sin<XYZ>{current_edge.edge_type}, Sin<LinComArrZ<XYPi>>{current_edge.polar_angle});

        // a = current_edge.polar_angle
        // b = current_edge.edge_type
        const auto sum = [&]() {
            LinComArrZ<XYPi> builder{};
            builder.add(current_edge.polar_angle);
            builder.add(xyz_to_xypi(current_edge.edge_type));
            return builder;
        }();

        const auto diff = [&]() {
            LinComArrZ<XYPi> builder{};
            builder.add(current_edge.polar_angle);
            builder.sub(xyz_to_xypi(current_edge.edge_type));
            return builder;
        }();

        const auto sin_sum = simplify_sin_xypi(sum);
        const auto sin_diff = simplify_sin_xypi(diff);

        // cos(a) * sin(b) = 1/2 sin(a + b) - 1/2 sin(a - b)
        coord_x.add(sin_sum.first, sin_sum.second);
        coord_x.sub(sin_diff.first, sin_diff.second);

        const auto cos_sum = simplify_cos_xypi(sum);
        const auto cos_diff = simplify_cos_xypi(diff);

        // sin(a) * sin(b) = 1/2 cos(a - b) - 1/2 cos(a + b)
        coord_y.add(cos_diff.first, cos_diff.second);
        coord_y.sub(cos_sum.first, cos_sum.second);
    }

    /*
    bool first = true;
    for (const auto& p : xs) {
        if (!first) {
            std::cout << '+';
        }

        std::cout << p.first << '*' << p.second;
        first = false;
    }
    std::cout << std::endl;

    first = true;
    for (const auto& p : ys) {
        if (!first) {
            std::cout << '+';
        }

        std::cout << p.first << '*' << p.second;
        first = false;
    }
    std::cout << std::endl;
    */

    //std::cout << xs << std::endl;
    //std::cout << ys << std::endl;
    //std::cout << "( " << coord_x << ", " << coord_y << ")" << std::endl;
    return {coord_x, coord_y};
}

std::pair<Equation<Sin>, Equation<Cos>> Unfolding::shooting_vector_general() const {

    // TODO does it matter whether we get the shooting path from the left or right side?

    const Vertex first_left = left_vertices.at(0);
    const Vertex last_left = left_vertices.at(left_vertices.size() - 1);

    const auto shooting_path_left = find_path(first_left, last_left);

    const Vertex first_right = right_vertices.at(0);
    const Vertex last_right = right_vertices.at(right_vertices.size() - 1);

    const auto shooting_path_right = find_path(first_right, last_right);

    const auto& shooting_path = shooting_path_left.size() < shooting_path_right.size() ? shooting_path_left : shooting_path_right;

    const auto shooting_vector = path_vector(shooting_path);

    return shooting_vector;
}

std::set<std::pair<Equation<Sin>, Equation<Cos>>> Unfolding::get_all_vectors() const{

        std::set<std::pair<Equation<Sin>, Equation<Cos>>> vector_set;
        for (size_t i = 0; i < left_vertices.size() - 1; i += 1) {
               const Vertex left_vertex = left_vertices.at(i);
               for (size_t j = 0; j < right_vertices.size() - 1; j += 1) {
                   const Vertex right_vertex = right_vertices.at(j);
                   const auto path = find_path(left_vertex, right_vertex);
                   const auto path_vec = path_vector(path);
                   //const auto& path_vector_x = path_vec.first;
                   //const auto& path_vector_y = path_vec.second;
                   vector_set.insert(path_vec);
               }
        }
        return vector_set;


}

template <template <typename> class T, template <typename> class S>
Curves Unfolding::generate_curves(const Equation<T>& shooting_vector_x, const Equation<S>& shooting_vector_y, const InitialAngles& initial_angles) const {

    Curves curves{};

    // we don't need to get the equations for the last vertices on the left and right sides, since those wrap around
    // and are the same as the first points on the left and right
    for (size_t i = 0; i < left_vertices.size() - 1; i += 1) {
        const Vertex left_vertex = left_vertices.at(i);
        for (size_t j = 0; j < right_vertices.size() - 1; j += 1) {
            const Vertex right_vertex = right_vertices.at(j);

            const auto path = find_path(left_vertex, right_vertex);

            const auto path_vec = path_vector(path);

            const auto& path_vector_x = path_vec.first;
            const auto& path_vector_y = path_vec.second;

            // equation = path_vector_x * shooting_vector_y - shooting_vector_x * path_vector_y;
            auto first = multiply_lin_com(shooting_vector_y, path_vector_x);

            const auto second = multiply_lin_com(path_vector_y, shooting_vector_x);

            first.sub(second);
            first.divide_content();

            divide_out_lines(first, curves, initial_angles.first, initial_angles.second);
        }
    }

    return curves;
}

template Curves Unfolding::generate_curves(const Equation<Sin>& shooting_vector_x, const Equation<Cos>& shooting_vector_y, const InitialAngles& initial_angles) const;
template Curves Unfolding::generate_curves(const Equation<Cos>& shooting_vector_x, const Equation<Sin>& shooting_vector_y, const InitialAngles& initial_angles) const;

template <template <typename> class T, template <typename> class S>
Curves Unfolding::generate_curves(const Equation<T>& shooting_vector_x, const Equation<S>& shooting_vector_y, const InitialAngles& initial_angles, const PointQ& center, const Rational& rx, const Rational& ry) const {

    Inserter insert{center, rx, ry};

    // we don't need to get the equations for the last vertices on the left and right sides, since those wrap around
    // and are the same as the first points on the left and right
    for (size_t i = 0; i < left_vertices.size() - 1; i += 1) {
        const Vertex left_vertex = left_vertices.at(i);
        for (size_t j = 0; j < right_vertices.size() - 1; j += 1) {
            const Vertex right_vertex = right_vertices.at(j);

            const auto path = find_path(left_vertex, right_vertex);

            const auto path_vec = path_vector(path);

            const auto& path_vector_x = path_vec.first;
            const auto& path_vector_y = path_vec.second;

            // equation = path_vector_x * shooting_vector_y - shooting_vector_x * path_vector_y;
            auto first = multiply_lin_com(shooting_vector_y, path_vector_x);

            const auto second = multiply_lin_com(path_vector_y, shooting_vector_x);

            first.sub(second);
            first.divide_content();

            divide_out_lines(first, initial_angles.first, initial_angles.second, insert);
        }
    }

    return insert.curves;
}

template Curves Unfolding::generate_curves(const Equation<Sin>& shooting_vector_x, const Equation<Cos>& shooting_vector_y, const InitialAngles& initial_angles, const PointQ& center, const Rational& rx, const Rational& ry) const;
template Curves Unfolding::generate_curves(const Equation<Cos>& shooting_vector_x, const Equation<Sin>& shooting_vector_y, const InitialAngles& initial_angles, const PointQ& center, const Rational& rx, const Rational& ry) const;

template <template <typename> class T, template <typename> class S>
CurvesLR Unfolding::generate_curves_lr(const Equation<T>& shooting_vector_x, const Equation<S>& shooting_vector_y) const {

    // could be unordered_map, but worry about that later
    //std::map<std::pair<LinComZ<Sin<EnumComZ<XY>>>, LinComZ<Cos<EnumComZ<XY>>>>, std::vector<LeftRight>> path_vectors;
    CurvesLR curves;

    // we don't need to get the equations for the last vertices on the left and right sides, since those wrap around
    // and are the same as the first points on the left and right
    for (size_t i = 0; i < left_vertices.size() - 1; i += 1) {
        const Vertex left_vertex = left_vertices.at(i);
        for (size_t j = 0; j < right_vertices.size() - 1; j += 1) {
            const Vertex right_vertex = right_vertices.at(j);

            const auto path = find_path(left_vertex, right_vertex);

            const auto path_vec = path_vector(path);

            const auto& path_vector_x = path_vec.first;
            const auto& path_vector_y = path_vec.second;


            // equation = path_vector_x * shooting_vector_y - shooting_vector_x * path_vector_y;
            auto first = multiply_lin_com(shooting_vector_y, path_vector_x);

            const auto second = multiply_lin_com(path_vector_y, shooting_vector_x);

            first.sub(second);
            first.divide_content();

            const LeftRight left_right{left_vertex, right_vertex};
            divide_out_lines_lr(first, curves, left_right);

            //const auto curve = first;
            //insert(curve, left_right, curves);
        }
    }

    // Sort the vectors just in case
    for (auto& kv : curves.first) {
        auto& vec = kv.second;
        falgo::sort(vec);
    }

    for (auto& kv : curves.second) {
        auto& vec = kv.second;
        falgo::sort(vec);
    }

    // IMPORTANT: Uncomment the next section if you want to see all of the equations
    // with their left_right stuff, not just the final ones.
    // Begin section
    /*
    for (const auto& kv : curves.first) {
        std::cout << kv.first << ", " << kv.second << std::endl;
    }

    for (const auto& kv : curves.second) {
        std::cout << kv.first << ", " << kv.second << std::endl;
    }
    std::cout << std::endl;

    // End section*/

    return curves;
}

template CurvesLR Unfolding::generate_curves_lr(const Equation<Sin>& shooting_vector_x, const Equation<Cos>& shooting_vector_y) const;
template CurvesLR Unfolding::generate_curves_lr(const Equation<Cos>& shooting_vector_x, const Equation<Sin>& shooting_vector_y) const;

template <template <typename> class T, template <typename> class S>
CurvesLR Unfolding::generate_curves_lr(const Equation<T>& shooting_vector_x, const Equation<S>& shooting_vector_y, const std::vector<LeftRight>& left_rights) const {

    CurvesLR curves;

    for (const auto& left_right : left_rights) {

        const auto path = find_path(left_right.left, left_right.right);

        const auto path_vec = path_vector(path);

        const auto& path_vector_x = path_vec.first;
        const auto& path_vector_y = path_vec.second;

        // equation = path_vector_x * shooting_vector_y - shooting_vector_x * path_vector_y;
        auto first = multiply_lin_com(shooting_vector_y, path_vector_x);

        const auto second = multiply_lin_com(path_vector_y, shooting_vector_x);

        first.sub(second);
        first.divide_content();

        divide_out_lines_lr(first, curves, left_right);

        //const auto curve = first;
        //insert(curve, left_right, curves);
    }

    return curves;
}

template CurvesLR Unfolding::generate_curves_lr(const Equation<Sin>& shooting_vector_x, const Equation<Cos>& shooting_vector_y, const std::vector<LeftRight>& left_rights) const;
template CurvesLR Unfolding::generate_curves_lr(const Equation<Cos>& shooting_vector_x, const Equation<Sin>& shooting_vector_y, const std::vector<LeftRight>& left_rights) const;
